package br.com.gameshelf;

import br.com.gameshelf.service.diagnostics.*;
import br.com.gameshelf.service.i18n.LanguagePreference;
import br.com.gameshelf.service.i18n.LocalizationService;
import br.com.gameshelf.ui.Artwork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class DiagnosticsTest {
    @TempDir Path dir;

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Test
    void diagnosesSteamEpicRobloxAndUnimplementedPlatformsWithInvalidManifests() throws Exception {
        Path steamRoot = dir.resolve("Steam");
        Path secondaryDrive = dir.resolve("SecondDrive");
        Files.createDirectories(steamRoot.resolve("steamapps"));
        Path validGameDir = Files.createDirectories(secondaryDrive.resolve("steamapps/common/PortalTwo"));
        Files.writeString(steamRoot.resolve("steamapps/libraryfolders.vdf"),
                "\"libraryfolders\" { \"1\" { \"path\" \"" + escape(secondaryDrive.toString()) + "\" } }");
        Files.writeString(secondaryDrive.resolve("steamapps/appmanifest_620.acf"),
                "\"AppState\" { \"appid\" \"620\" \"name\" \"Portal 2\" \"installdir\" \"PortalTwo\" \"StateFlags\" \"4\" }");
        Files.writeString(secondaryDrive.resolve("steamapps/appmanifest_228980.acf"),
                "\"AppState\" { \"appid\" \"228980\" \"name\" \"Steamworks Common Redistributables\" \"installdir\" \"Steamworks\" \"StateFlags\" \"4\" }");
        Files.writeString(secondaryDrive.resolve("steamapps/appmanifest_broken.acf"),
                "\"AppState\" { \"appid\" ");

        Path epicManifests = Files.createDirectories(dir.resolve("EpicManifests"));
        Path epicGameDir = Files.createDirectories(dir.resolve("EpicGame"));
        Files.createFile(epicGameDir.resolve("Game.exe"));
        Files.writeString(epicManifests.resolve("valid.item"),
                "{\"AppName\":\"epic1\",\"DisplayName\":\"Valid Epic\",\"InstallLocation\":\"" + escape(epicGameDir.toString()) + "\",\"LaunchExecutable\":\"Game.exe\",\"bIsIncompleteInstall\":false}");
        Files.writeString(epicManifests.resolve("dlc.item"),
                "{\"AppName\":\"dlc1\",\"DisplayName\":\"Some DLC\",\"InstallLocation\":\"" + escape(epicGameDir.toString()) + "\",\"LaunchExecutable\":\"Game.exe\",\"bIsDLC\":true}");
        Files.writeString(epicManifests.resolve("broken.item"), "{not-valid-json");

        Path robloxVersions = dir.resolve("Roblox/Versions");
        Path rVer = Files.createDirectories(robloxVersions.resolve("version-abc"));
        Files.createFile(rVer.resolve("RobloxPlayerBeta.exe"));

        Library lib = new Library(dir.resolve("data/library.properties"));
        lib.add(new Game("local:manual1", "Manual Game", "Local", dir.toString(), "C:/Games/Manual.exe", "", true, "", 0, false));

        List<PlatformDiagnostics.PlatformReport> reports = PlatformDiagnostics.inspectAll(
                lib, MainWindow.PLATFORMS, List.of(steamRoot), epicManifests, robloxVersions, new File[0]);

        var steam = reports.stream().filter(r -> r.canonicalName().equals("Steam")).findFirst().orElseThrow();
        assertEquals(PlatformDiagnostics.Health.WARNING, steam.health());
        assertTrue(steam.launcherFound());
        assertEquals(1, steam.gamesCount());
        assertEquals(1, steam.steamDetails().invalidManifests().size());
        assertEquals(1, steam.steamDetails().ignoredGames().size());

        var epic = reports.stream().filter(r -> r.canonicalName().equals("Epic Games")).findFirst().orElseThrow();
        assertEquals(PlatformDiagnostics.Health.WARNING, epic.health());
        assertEquals(1, epic.gamesCount());
        assertEquals(1, epic.epicDetails().invalidManifests().size());
        assertEquals(1, epic.epicDetails().ignoredGames().size());

        var local = reports.stream().filter(r -> r.canonicalName().equals("Local")).findFirst().orElseThrow();
        assertEquals(PlatformDiagnostics.Health.SUCCESS, local.health());
        assertEquals(2, local.gamesCount()); // 1 manual + 1 Roblox inside Local
        assertFalse(reports.stream().anyMatch(r -> r.canonicalName().equals("Roblox")));

        var gog = reports.stream().filter(r -> r.canonicalName().equals("GOG")).findFirst().orElseThrow();
        assertEquals(PlatformDiagnostics.DetectorKind.NOT_IMPLEMENTED, gog.detectorKind());
        assertEquals(PlatformDiagnostics.Health.INACTIVE, gog.health());
    }

    @Test
    void treatsNoInstalledGamesAsNonError() throws Exception {
        Library lib = new Library(dir.resolve("empty/library.properties"));
        List<PlatformDiagnostics.PlatformReport> reports = PlatformDiagnostics.inspectAll(
                lib, MainWindow.PLATFORMS, List.of(dir.resolve("no-steam")),
                dir.resolve("no-epic"), dir.resolve("no-roblox"), new File[0]);

        for (var r : reports) {
            assertNotEquals(PlatformDiagnostics.Health.ERROR, r.health(), "Missing games must not report ERROR: " + r.canonicalName());
        }
    }

    @Test
    void sanitizesSensitivePathsSecretsAndExportsTxtAndJsonWithoutPersonalHistory() throws Exception {
        String raw = "C:\\Users\\NomeDoUsuario\\AppData\\Local\\GameShelf\\library.db "
                + "Bearer super-secret-token-123 gridDbKey=my-personal-key user@example.com ghp_1234567890abcdef";
        String sanitized = DiagnosticSanitizer.sanitize(raw, List.of("custom-secret-xyz"));
        assertFalse(sanitized.contains("NomeDoUsuario"));
        assertFalse(sanitized.contains("super-secret-token-123"));
        assertFalse(sanitized.contains("my-personal-key"));
        assertFalse(sanitized.contains("user@example.com"));
        assertFalse(sanitized.contains("ghp_1234567890abcdef"));
        assertTrue(sanitized.contains("%USERPROFILE%"));

        Path dataDir = Files.createDirectories(dir.resolve("appdata"));
        Library lib = new Library(dataDir.resolve("library.properties"));
        lib.add(new Game("local:secret-game", "Private Secret Game Title", "Local", "", "C:\\Users\\Alice\\Games\\Secret.exe", "", true, "", 9999, true));
        lib.settingValue("gridDbKey", "sgdb-secret-999");
        lib.save();

        Playtime tracker = new Playtime(lib);
        Artwork art = new Artwork(dataDir);
        AppLogger logger = new AppLogger(dataDir, 1024);
        logger.info("Test", "Opened C:\\Users\\Alice\\AppData\\Local\\GameShelf with token=abc12345");
        // Trigger log rotation
        for (int i = 0; i < 40; i++) {
            logger.debug("Loop", "Padding log entry number " + i + " for rotation test");
        }
        assertTrue(Files.exists(dataDir.resolve("logs/gameshelf.log")));
        assertTrue(Files.exists(dataDir.resolve("logs/gameshelf.1.log")));

        DiagnosticsService svc = new DiagnosticsService(lib, tracker, art, logger, MainWindow.PLATFORMS);
        assertEquals("ok", svc.runIntegrityCheck());
        assertEquals(1, lib.snapshot().size()); // Non-destructive

        DiagnosticReport report = svc.snapshot(true, "Biblioteca local");
        LocalizationService i18n = new LocalizationService(LanguagePreference.EN_US);
        String txt = report.toSanitizedText(i18n, List.of("sgdb-secret-999"));
        String json = report.toSanitizedJson(List.of("sgdb-secret-999"));

        for (String exported : List.of(txt, json)) {
            assertFalse(exported.contains("Alice"));
            assertFalse(exported.contains("sgdb-secret-999"));
            assertFalse(exported.contains("Private Secret Game Title"), "Full personal game history must not be exported");
            assertFalse(exported.contains("abc12345"));
        }
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"integrityCheckResult\": \"ok\""));
        art.close();
    }

    @Test
    void processDiagnosticsDetectsConflictsAndMatchesActivePids() throws Exception {
        Library lib = new Library(dir.resolve("proc/library.properties"));
        lib.add(new Game("local:1", "Game A", "Local", "C:/Games/A", "C:/Games/Shared/app.exe", "", true, "", 0, false));
        lib.add(new Game("local:2", "Game B", "Local", "C:/Games/B", "C:/Games/Shared/app.exe", "", true, "", 0, false));

        Playtime tracker = new Playtime(lib);
        tracker.tick(List.of("C:/Games/Shared/app.exe"), 0);
        var snap = ProcessDiagnostics.capture(lib, tracker, true);
        assertEquals(2, snap.monitoredGames().size());
        assertEquals(1, snap.conflicts().size());
        assertTrue(snap.monitoredGames().stream().allMatch(ProcessDiagnostics.MonitoredGameInfo::activeSession));
    }
}
