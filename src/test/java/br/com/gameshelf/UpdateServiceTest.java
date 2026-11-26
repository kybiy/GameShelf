package br.com.gameshelf;

import br.com.gameshelf.service.update.ReleaseInfo;
import br.com.gameshelf.service.update.UpdateChannel;
import br.com.gameshelf.service.update.UpdateService;
import br.com.gameshelf.service.update.VersionComparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

final class UpdateServiceTest {
    @TempDir Path dir;

    private static final String SAMPLE_RELEASES = """
            [
              {
                "tag_name": "v0.1.0-alpha.1",
                "name": "GameShelf v0.1.0 — Alpha 1",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-11-26T13:34:11Z",
                "html_url": "https://github.com/kybiy/GameShelf/releases/tag/v0.1.0-alpha.1",
                "body": "Initial alpha",
                "assets": []
              },
              {
                "tag_name": "v0.1.0-alpha.2",
                "name": "GameShelf v0.1.0 — Alpha 2",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-11-27T10:00:00Z",
                "html_url": "https://github.com/kybiy/GameShelf/releases/tag/v0.1.0-alpha.2",
                "body": "- Better detection",
                "assets": [
                  {
                    "name": "GameShelf-0.1.0-alpha.2-win-x64.exe",
                    "size": 62000000,
                    "browser_download_url": "https://github.com/kybiy/GameShelf/releases/download/v0.1.0-alpha.2/GameShelf-0.1.0-alpha.2-win-x64.exe"
                  },
                  {
                    "name": "GameShelf-0.1.0-alpha.2-portable-win-x64.zip",
                    "size": 60000000,
                    "browser_download_url": "https://github.com/kybiy/GameShelf/releases/download/v0.1.0-alpha.2/GameShelf-0.1.0-alpha.2-portable-win-x64.zip"
                  }
                ]
              },
              {
                "tag_name": "v0.2.0-beta.1",
                "name": "Draft Release",
                "prerelease": true,
                "draft": true,
                "published_at": "2026-11-28T10:00:00Z",
                "html_url": "https://github.com/kybiy/GameShelf/releases/tag/v0.2.0-beta.1",
                "body": "Draft should be ignored",
                "assets": []
              },
              {
                "tag_name": "v0.0.9",
                "name": "Old Stable",
                "prerelease": false,
                "draft": false,
                "published_at": "2026-10-01T10:00:00Z",
                "html_url": "https://github.com/kybiy/GameShelf/releases/tag/v0.0.9",
                "body": "Older release",
                "assets": []
              }
            ]
            """;

    @Test
    void comparesStableAlphaAndBetaVersionsCorrectly() {
        assertTrue(VersionComparator.compare("0.1.0-alpha.2", "0.1.0-alpha.1") > 0);
        assertTrue(VersionComparator.compare("0.1.0-alpha.10", "0.1.0-alpha.2") > 0);
        assertTrue(VersionComparator.compare("0.1.0-beta.1", "0.1.0-alpha.99") > 0);
        assertTrue(VersionComparator.compare("0.1.0", "0.1.0-beta.5") > 0);
        assertTrue(VersionComparator.compare("0.1.0", "0.0.9") > 0);
        assertEquals(0, VersionComparator.compare("v0.1.0-alpha.1", "0.1.0-alpha.1"));
        assertEquals(VersionComparator.Stage.ALPHA, VersionComparator.stage("0.1.0-alpha.1"));
        assertEquals(VersionComparator.Stage.BETA, VersionComparator.stage("0.1.0-beta.2"));
        assertEquals(VersionComparator.Stage.STABLE, VersionComparator.stage("0.1.0"));
    }

    @Test
    void selectsNewerReleaseByChannelAndIgnoresDraftsAndOlderVersions() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC);
        UpdateService service = new UpdateService("0.1.0-alpha.1",
                (url, etag) -> new UpdateService.TransportResponse(200, SAMPLE_RELEASES, "\"etag1\""), fixed);

        var experimental = service.check(UpdateChannel.EXPERIMENTAL, "", true);
        assertEquals(UpdateService.Status.UPDATE_AVAILABLE, experimental.status());
        ReleaseInfo info = experimental.update().orElseThrow();
        assertEquals("0.1.0-alpha.2", info.version());
        assertEquals("GameShelf-0.1.0-alpha.2-win-x64.exe", info.preferredInstaller().orElseThrow().name());

        // Stable channel skips alpha.2 and does not treat old v0.0.9 as an update
        var stable = service.check(UpdateChannel.STABLE, "", true);
        assertEquals(UpdateService.Status.UP_TO_DATE, stable.status());
        assertTrue(stable.update().isEmpty());
    }

    @Test
    void respectsIgnoredVersionOnAutoCheckAndPersistsPreferences() throws Exception {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC);
        UpdateService service = new UpdateService("0.1.0-alpha.1",
                (url, etag) -> new UpdateService.TransportResponse(200, SAMPLE_RELEASES, ""), fixed);

        assertEquals(UpdateService.Status.UP_TO_DATE,
                service.check(UpdateChannel.EXPERIMENTAL, "0.1.0-alpha.2", false).status());
        assertEquals(UpdateService.Status.UPDATE_AVAILABLE,
                service.check(UpdateChannel.EXPERIMENTAL, "0.1.0-alpha.2", true).status());

        Path legacy = dir.resolve("library.properties");
        Library lib = new Library(legacy);
        lib.settingValue("update.channel", UpdateChannel.STABLE.code());
        lib.settingValue("update.auto", "false");
        lib.settingValue("update.ignored", "0.1.0-alpha.2");
        lib.save();

        Library reopened = new Library(legacy);
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromCode(reopened.setting("update.channel", ""), "0.1.0-alpha.1"));
        assertEquals("false", reopened.setting("update.auto", "true"));
        assertEquals("0.1.0-alpha.2", reopened.setting("update.ignored", ""));
    }

    @Test
    void handlesConnectionFailuresRateLimitsAndMalformedPayloadsGracefully() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC);

        UpdateService offline = new UpdateService("0.1.0-alpha.1", (url, etag) -> {
            throw new IOException("Timed out");
        }, fixed);
        assertEquals(UpdateService.Status.OFFLINE_OR_ERROR, offline.check(UpdateChannel.EXPERIMENTAL, "", true).status());

        UpdateService rateLimited = new UpdateService("0.1.0-alpha.1",
                (url, etag) -> new UpdateService.TransportResponse(403, "rate limit exceeded", ""), fixed);
        assertEquals(UpdateService.Status.RATE_LIMITED, rateLimited.check(UpdateChannel.EXPERIMENTAL, "", true).status());

        UpdateService invalidJson = new UpdateService("0.1.0-alpha.1",
                (url, etag) -> new UpdateService.TransportResponse(200, "{not-an-array", ""), fixed);
        assertEquals(UpdateService.Status.OFFLINE_OR_ERROR, invalidJson.check(UpdateChannel.EXPERIMENTAL, "", true).status());
    }

    @Test
    void rejectsUnofficialDownloadOrHtmlUrls() {
        String spoofed = """
                [
                  {
                    "tag_name": "v9.9.9",
                    "name": "Fake",
                    "prerelease": false,
                    "draft": false,
                    "html_url": "https://evil.example.com/release",
                    "assets": [
                      {
                        "name": "GameShelf.exe",
                        "size": 123,
                        "browser_download_url": "https://evil.example.com/GameShelf.exe"
                      }
                    ]
                  }
                ]
                """;
        Clock fixed = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC);
        UpdateService service = new UpdateService("0.1.0-alpha.1",
                (url, etag) -> new UpdateService.TransportResponse(200, spoofed, ""), fixed);
        assertEquals(UpdateService.Status.UP_TO_DATE, service.check(UpdateChannel.EXPERIMENTAL, "", true).status());
    }
}
