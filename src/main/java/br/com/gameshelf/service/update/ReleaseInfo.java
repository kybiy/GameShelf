package br.com.gameshelf.service.update;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Represents a validated GitHub Release entry for GameShelf. */
public record ReleaseInfo(
        String tagName,
        String version,
        String name,
        String body,
        Instant publishedAt,
        boolean prerelease,
        boolean draft,
        String htmlUrl,
        List<Asset> assets
) {
    private static final String OFFICIAL_RELEASE_PREFIX = "https://github.com/kybiy/GameShelf/releases/";
    private static final String OFFICIAL_DOWNLOAD_PREFIX = "https://github.com/kybiy/GameShelf/releases/download/";

    public record Asset(String name, long size, String downloadUrl) {
        public boolean hasOfficialUrl() {
            return downloadUrl != null && downloadUrl.startsWith(OFFICIAL_DOWNLOAD_PREFIX);
        }
    }

    public boolean hasOfficialHtmlUrl() {
        return htmlUrl != null && htmlUrl.startsWith(OFFICIAL_RELEASE_PREFIX);
    }

    public VersionComparator.Stage stage() {
        VersionComparator.Stage s = VersionComparator.stage(version);
        if (prerelease && s == VersionComparator.Stage.STABLE) return VersionComparator.Stage.BETA;
        return s;
    }

    public Optional<Asset> preferredInstaller() {
        return assets.stream()
                .filter(Asset::hasOfficialUrl)
                .filter(a -> a.name().toLowerCase(Locale.ROOT).endsWith(".exe"))
                .findFirst();
    }

    public Optional<Asset> preferredPortable() {
        return assets.stream()
                .filter(Asset::hasOfficialUrl)
                .filter(a -> a.name().toLowerCase(Locale.ROOT).endsWith(".zip"))
                .findFirst();
    }

    public Optional<Asset> primaryDownloadAsset() {
        return preferredInstaller().or(this::preferredPortable);
    }

    public String safeDownloadUrl() {
        return primaryDownloadAsset()
                .map(Asset::downloadUrl)
                .filter(u -> u.startsWith(OFFICIAL_DOWNLOAD_PREFIX))
                .orElse(hasOfficialHtmlUrl() ? htmlUrl : AppVersion.REPO_RELEASES_WEB);
    }
}
