package br.com.gameshelf.service.update;

import br.com.gameshelf.ManifestParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Checks GitHub Releases for newer GameShelf versions without requiring authentication
 * or blocking the JavaFX Application Thread.
 */
public final class UpdateService {
    public enum Status { UP_TO_DATE, UPDATE_AVAILABLE, RATE_LIMITED, OFFLINE_OR_ERROR }

    public record TransportResponse(int statusCode, String body, String etag) {}

    @FunctionalInterface
    public interface ReleaseTransport {
        TransportResponse fetch(String url, String etag) throws IOException, InterruptedException;
    }

    public record CheckResult(Status status, Optional<ReleaseInfo> update, String errorMessage, Instant checkedAt) {}

    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private final String currentVersion;
    private final ReleaseTransport transport;
    private final Clock clock;

    private List<ReleaseInfo> cachedReleases = List.of();
    private String lastEtag = "";
    private Instant lastFetchAt;

    public UpdateService() {
        this(AppVersion.CURRENT, defaultTransport(), Clock.systemUTC());
    }

    public UpdateService(String currentVersion, ReleaseTransport transport, Clock clock) {
        this.currentVersion = currentVersion;
        this.transport = transport;
        this.clock = clock;
    }

    private static final class ClientHolder {
        static final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
    public static ReleaseTransport defaultTransport() {
        return (url, etag) -> {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "GameShelf/" + AppVersion.CURRENT)
                    .GET();
            if (etag != null && !etag.isBlank()) {
                req.header("If-None-Match", etag);
            }
            br.com.gameshelf.NetworkPolicy.requireOnline();
            HttpResponse<String> resp = ClientHolder.client.send(req.build(), HttpResponse.BodyHandlers.ofString());
            String respEtag = resp.headers().firstValue("ETag").orElse("");
            return new TransportResponse(resp.statusCode(), resp.body() == null ? "" : resp.body(), respEtag);
        };
    }

    public synchronized CheckResult check(UpdateChannel channel, String ignoredVersion, boolean manual) {
        Instant now = clock.instant();
        if (!manual && lastFetchAt != null && Duration.between(lastFetchAt, now).compareTo(COOLDOWN) < 0) {
            return evaluate(cachedReleases, channel, ignoredVersion, manual, lastFetchAt);
        }

        try {
            TransportResponse resp = transport.fetch(AppVersion.REPO_RELEASES_API, lastEtag);
            int code = resp.statusCode();
            if (code == 304 && !cachedReleases.isEmpty()) {
                lastFetchAt = now;
                return evaluate(cachedReleases, channel, ignoredVersion, manual, now);
            }
            if (code == 403 || code == 429) {
                return new CheckResult(Status.RATE_LIMITED, Optional.empty(), "HTTP " + code, now);
            }
            if (code < 200 || code >= 300) {
                return new CheckResult(Status.OFFLINE_OR_ERROR, Optional.empty(), "HTTP " + code, now);
            }
            List<ReleaseInfo> parsed = parseReleases(resp.body());
            cachedReleases = parsed;
            if (resp.etag() != null && !resp.etag().isBlank()) lastEtag = resp.etag();
            lastFetchAt = now;
            return evaluate(parsed, channel, ignoredVersion, manual, now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CheckResult(Status.OFFLINE_OR_ERROR, Optional.empty(), e.getMessage(), now);
        } catch (Exception e) {
            return new CheckResult(Status.OFFLINE_OR_ERROR, Optional.empty(), e.getMessage(), now);
        }
    }

    private CheckResult evaluate(List<ReleaseInfo> releases, UpdateChannel channel,
                                 String ignoredVersion, boolean manual, Instant checkedAt) {
        Optional<ReleaseInfo> best = releases.stream()
                .filter(r -> !r.draft())
                .filter(r -> channel == UpdateChannel.EXPERIMENTAL || (!r.prerelease() && r.stage() == VersionComparator.Stage.STABLE))
                .filter(r -> VersionComparator.isValid(r.version()))
                .filter(r -> VersionComparator.compare(r.version(), currentVersion) > 0)
                .max((a, b) -> VersionComparator.compare(a.version(), b.version()));

        if (best.isEmpty()) {
            return new CheckResult(Status.UP_TO_DATE, Optional.empty(), "", checkedAt);
        }
        ReleaseInfo candidate = best.get();
        if (!manual && ignoredVersion != null && !ignoredVersion.isBlank()
                && VersionComparator.compare(candidate.version(), ignoredVersion) == 0) {
            return new CheckResult(Status.UP_TO_DATE, Optional.empty(), "", checkedAt);
        }
        return new CheckResult(Status.UPDATE_AVAILABLE, Optional.of(candidate), "", checkedAt);
    }

    static List<ReleaseInfo> parseReleases(String json) {
        List<Map<String, Object>> rows = ManifestParser.jsonArray(json);
        List<ReleaseInfo> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String tag = ManifestParser.str(row, "tag_name");
            String version = VersionComparator.normalize(tag);
            if (tag.isBlank() || !VersionComparator.isValid(version)) continue;

            boolean draft = Boolean.TRUE.equals(row.get("draft"));
            boolean prerelease = Boolean.TRUE.equals(row.get("prerelease"));
            String name = ManifestParser.str(row, "name");
            if (name.isBlank()) name = tag;
            String body = ManifestParser.str(row, "body");
            String htmlUrl = ManifestParser.str(row, "html_url");
            Instant publishedAt = parseInstant(ManifestParser.str(row, "published_at"));

            List<ReleaseInfo.Asset> assets = new ArrayList<>();
            if (row.get("assets") instanceof List<?> rawAssets) {
                for (Object rawAsset : rawAssets) {
                    Map<String, Object> a = ManifestParser.map(rawAsset);
                    String assetName = ManifestParser.str(a, "name");
                    String url = ManifestParser.str(a, "browser_download_url");
                    long size = a.get("size") instanceof Number n ? n.longValue() : -1L;
                    if (!assetName.isBlank() && !url.isBlank()) {
                        ReleaseInfo.Asset parsedAsset = new ReleaseInfo.Asset(assetName, size, url);
                        if (parsedAsset.hasOfficialUrl()) {
                            assets.add(parsedAsset);
                        }
                    }
                }
            }
            ReleaseInfo info = new ReleaseInfo(tag, version, name, body, publishedAt, prerelease, draft, htmlUrl, List.copyOf(assets));
            if (info.hasOfficialHtmlUrl()) {
                out.add(info);
            }
        }
        return List.copyOf(out);
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }
}
