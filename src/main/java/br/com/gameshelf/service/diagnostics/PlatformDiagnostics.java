package br.com.gameshelf.service.diagnostics;

import br.com.gameshelf.Detector;
import br.com.gameshelf.Game;
import br.com.gameshelf.Library;
import br.com.gameshelf.ManifestParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Inspects all supported and planned game platforms without altering user files or processes.
 * Absence of installed games is never treated as an error.
 */
public final class PlatformDiagnostics {
    public enum Health { SUCCESS, WARNING, ERROR, INACTIVE }

    public enum DetectorKind { IMPLEMENTED, EXPERIMENTAL, NOT_IMPLEMENTED }

    public record IgnoredEntry(String name, String reason) {}

    public record SteamDetails(
            List<String> mainRoots,
            List<String> vdfFiles,
            List<String> libraryFolders,
            List<String> acfFiles,
            List<String> appIds,
            List<String> installDirs,
            List<String> invalidManifests,
            List<IgnoredEntry> ignoredGames
    ) {}

    public record EpicDetails(
            List<String> manifestDirs,
            List<String> itemFiles,
            List<String> gameNames,
            List<String> installDirs,
            List<String> executables,
            List<String> invalidManifests,
            List<IgnoredEntry> ignoredGames
    ) {}

    public record PlatformReport(
            String canonicalName,
            Health health,
            DetectorKind detectorKind,
            boolean launcherFound,
            boolean gamesDetected,
            List<String> searchedDirs,
            List<String> installPathsFound,
            List<String> manifestFilesFound,
            int gamesCount,
            Instant checkedAt,
            long durationMs,
            List<String> warnings,
            List<String> errors,
            SteamDetails steamDetails,
            EpicDetails epicDetails
    ) {}

    private PlatformDiagnostics() {}

    public static List<PlatformReport> inspectAll(Library library, List<String> canonicalPlatforms) {
        Set<Path> steamRoots = new LinkedHashSet<>();
        addPath(steamRoots, Detector.registryValue("HKCU\\Software\\Valve\\Steam", "SteamPath"));
        addPath(steamRoots, Detector.registryValue("HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam", "InstallPath"));
        addPath(steamRoots, Detector.env("ProgramFiles(x86)", "Steam"));
        addPath(steamRoots, Detector.env("ProgramFiles", "Steam"));

        Path epicManifests = Path.of(Detector.env("ProgramData", "Epic/EpicGamesLauncher/Data/Manifests"));
        Path robloxVersions = Path.of(Detector.env("LOCALAPPDATA", "Roblox/Versions"));
        File[] drives = File.listRoots();

        return inspectAll(library, canonicalPlatforms, List.copyOf(steamRoots), epicManifests, robloxVersions, drives);
    }

    public static List<PlatformReport> inspectAll(
            Library library,
            List<String> canonicalPlatforms,
            List<Path> steamRoots,
            Path epicManifests,
            Path robloxVersions,
            File[] riotDrives
    ) {
        List<PlatformReport> out = new ArrayList<>();
        for (String platform : canonicalPlatforms) {
            out.add(switch (platform) {
                case "Steam" -> inspectSteam(steamRoots);
                case "Epic Games" -> inspectEpic(epicManifests);
                case "Riot Games" -> inspectRiot(riotDrives);
                case "Local" -> inspectLocal(library, robloxVersions);
                default -> unimplemented(platform);
            });
        }
        return List.copyOf(out);
    }

    static PlatformReport inspectSteam(List<Path> candidateRoots) {
        long start = System.nanoTime();
        Instant now = Instant.now();

        List<String> searched = candidateRoots.stream().map(Path::toString).toList();
        List<Path> existingRoots = candidateRoots.stream().filter(Files::isDirectory).toList();

        List<String> vdfs = new ArrayList<>();
        Set<Path> libs = new LinkedHashSet<>(existingRoots);
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Path root : existingRoots) {
            for (Path vdf : List.of(root.resolve("steamapps/libraryfolders.vdf"), root.resolve("config/libraryfolders.vdf"))) {
                if (!Files.isRegularFile(vdf)) continue;
                vdfs.add(vdf.toString());
                try {
                    Map<String, Object> data = ManifestParser.map(ManifestParser.vdf(br.com.gameshelf.Detector.readManifest(vdf)).get("libraryfolders"));
                    for (var e : data.entrySet()) {
                        if (e.getKey().matches("\\d+")) {
                            Object v = e.getValue();
                            addPath(libs, v instanceof String s ? s : ManifestParser.str(ManifestParser.map(v), "path"));
                        }
                    }
                } catch (Exception e) {
                    warnings.add(vdf.getFileName() + ": " + e.getMessage());
                }
            }
        }

        List<String> acfFiles = new ArrayList<>();
        List<String> appIds = new ArrayList<>();
        List<String> installDirs = new ArrayList<>();
        List<String> invalidManifests = new ArrayList<>();
        List<IgnoredEntry> ignored = new ArrayList<>();
        int detected = 0;

        for (Path lib : libs) {
            Path apps = lib.resolve("steamapps");
            if (!Files.isDirectory(apps)) continue;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(apps, "appmanifest_*.acf")) {
                for (Path f : files) {
                    acfFiles.add(f.toString());
                    try {
                        Map<String, Object> m = ManifestParser.map(ManifestParser.vdf(br.com.gameshelf.Detector.readManifest(f)).get("AppState"));
                        String id = ManifestParser.str(m, "appid");
                        String name = ManifestParser.str(m, "name");
                        String dir = ManifestParser.str(m, "installdir");
                        if (!id.matches("\\d+") || name.isBlank() || dir.isBlank()) {
                            invalidManifests.add(f.getFileName() + ": missing appid/name/installdir");
                            continue;
                        }
                        if (id.equals("228980") || name.toLowerCase(Locale.ROOT).startsWith("steamworks common redistributables")) {
                            ignored.add(new IgnoredEntry(name, "Shared runtime / redistributable"));
                            continue;
                        }
                        Path common = apps.resolve("common").toAbsolutePath().normalize();
                        Path folder = common.resolve(dir).normalize();
                        if (!folder.startsWith(common) || folder.equals(common) || !Files.isDirectory(folder)) {
                            ignored.add(new IgnoredEntry(name, "Install directory missing"));
                            continue;
                        }
                        String flags = ManifestParser.str(m, "StateFlags");
                        if (!flags.isBlank() && (Long.parseLong(flags) & 4) == 0) {
                            ignored.add(new IgnoredEntry(name, "StateFlags=" + flags + " (incomplete)"));
                            continue;
                        }
                        appIds.add(id);
                        installDirs.add(folder.toString());
                        detected++;
                    } catch (Exception e) {
                        invalidManifests.add(f.getFileName() + ": " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                errors.add("Steam (" + apps + "): " + e.getMessage());
            }
        }

        boolean launcherFound = !existingRoots.isEmpty();
        Health health;
        if (!errors.isEmpty()) health = Health.ERROR;
        else if (!launcherFound) health = Health.INACTIVE;
        else if (!invalidManifests.isEmpty() || !warnings.isEmpty()) health = Health.WARNING;
        else health = Health.SUCCESS;

        List<String> allWarnings = new ArrayList<>(warnings);
        invalidManifests.forEach(m -> allWarnings.add("Invalid manifest: " + m));

        SteamDetails details = new SteamDetails(
                existingRoots.stream().map(Path::toString).toList(),
                vdfs,
                libs.stream().map(Path::toString).toList(),
                acfFiles,
                appIds,
                installDirs,
                invalidManifests,
                ignored
        );

        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        return new PlatformReport(
                "Steam", health, DetectorKind.IMPLEMENTED, launcherFound, detected > 0,
                searched, libs.stream().map(Path::toString).toList(), acfFiles,
                detected, now, durationMs, List.copyOf(allWarnings), List.copyOf(errors),
                details, null
        );
    }

    static PlatformReport inspectEpic(Path manifestsDir) {
        long start = System.nanoTime();
        Instant now = Instant.now();

        List<String> searched = List.of(manifestsDir.toString());
        boolean launcherFound = Files.isDirectory(manifestsDir);
        List<String> itemFiles = new ArrayList<>();
        List<String> gameNames = new ArrayList<>();
        List<String> installDirs = new ArrayList<>();
        List<String> executables = new ArrayList<>();
        List<String> invalidManifests = new ArrayList<>();
        List<IgnoredEntry> ignored = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int detected = 0;

        if (launcherFound) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(manifestsDir, "*.item")) {
                for (Path f : files) {
                    itemFiles.add(f.toString());
                    try {
                        Map<String, Object> m = ManifestParser.json(br.com.gameshelf.Detector.readManifest(f));
                        String id = ManifestParser.str(m, "AppName");
                        String name = ManifestParser.str(m, "DisplayName");
                        String loc = ManifestParser.str(m, "InstallLocation");
                        String exe = ManifestParser.str(m, "LaunchExecutable");
                        if (id.isBlank() || name.isBlank() || loc.isBlank()) {
                            invalidManifests.add(f.getFileName() + ": missing AppName/DisplayName/InstallLocation");
                            continue;
                        }
                        if (Boolean.TRUE.equals(m.get("bIsIncompleteInstall"))) {
                            ignored.add(new IgnoredEntry(name, "Incomplete installation"));
                            continue;
                        }
                        if (Boolean.TRUE.equals(m.get("bIsDLC"))) {
                            ignored.add(new IgnoredEntry(name, "DLC entry"));
                            continue;
                        }
                        String main = ManifestParser.str(m, "MainGameAppName");
                        if (!main.isBlank() && !main.equals(id)) {
                            ignored.add(new IgnoredEntry(name, "Secondary entry for " + main));
                            continue;
                        }
                        Path folder = Path.of(loc).toAbsolutePath().normalize();
                        if (!Files.isDirectory(folder)) {
                            ignored.add(new IgnoredEntry(name, "Install directory missing"));
                            continue;
                        }
                        Path executable = folder.resolve(exe).normalize();
                        if (exe.isBlank() || !executable.startsWith(folder) || !Files.isRegularFile(executable)) {
                            ignored.add(new IgnoredEntry(name, "Executable missing"));
                            continue;
                        }
                        gameNames.add(name);
                        installDirs.add(folder.toString());
                        executables.add(executable.toString());
                        detected++;
                    } catch (Exception e) {
                        invalidManifests.add(f.getFileName() + ": " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                errors.add("Epic (" + manifestsDir + "): " + e.getMessage());
            }
        }

        Health health;
        if (!errors.isEmpty()) health = Health.ERROR;
        else if (!launcherFound) health = Health.INACTIVE;
        else if (!invalidManifests.isEmpty()) health = Health.WARNING;
        else health = Health.SUCCESS;

        List<String> warnings = invalidManifests.stream().map(m -> "Invalid manifest: " + m).toList();
        EpicDetails details = new EpicDetails(
                launcherFound ? List.of(manifestsDir.toString()) : List.of(),
                itemFiles, gameNames, installDirs, executables, invalidManifests, ignored
        );

        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        return new PlatformReport(
                "Epic Games", health, DetectorKind.IMPLEMENTED, launcherFound, detected > 0,
                searched, installDirs, itemFiles,
                detected, now, durationMs, warnings, List.copyOf(errors),
                null, details
        );
    }

    static PlatformReport inspectRiot(File[] drives) {
        long start = System.nanoTime();
        Instant now = Instant.now();
        List<String> searched = new ArrayList<>();
        List<String> installPaths = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean clientFound = false;
        int detected = 0;

        if (drives != null) {
            for (File drive : drives) {
                Path base = drive.toPath().resolve("Riot Games");
                searched.add(base.toString());
                Path client = base.resolve("Riot Client/RiotClientServices.exe");
                if (!Files.isRegularFile(client)) continue;
                clientFound = true;
                installPaths.add(base.toString());
                for (String[] game : new String[][]{
                        {"league_of_legends", "League of Legends/Game/League of Legends.exe"},
                        {"valorant", "VALORANT/live/ShooterGame/Binaries/Win64/VALORANT-Win64-Shipping.exe"}
                }) {
                    if (Files.isRegularFile(base.resolve(game[1]))) {
                        installPaths.add(base.resolve(game[1].split("/")[0]).toString());
                        detected++;
                    }
                }
            }
        }

        if (clientFound) warnings.add("Experimental detector: checks default <Drive>:\\Riot Games folders.");
        Health health = clientFound ? Health.WARNING : Health.INACTIVE;
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        return new PlatformReport(
                "Riot Games", health, DetectorKind.EXPERIMENTAL, clientFound, detected > 0,
                searched, installPaths, List.of(),
                detected, now, durationMs, warnings, List.of(), null, null
        );
    }

    static PlatformReport inspectLocal(Library library, Path robloxVersions) {
        long start = System.nanoTime();
        Instant now = Instant.now();

        List<String> searched = List.of(robloxVersions.toString());
        List<String> installPaths = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        boolean robloxFound = false;
        int robloxCount = 0;
        if (Files.isDirectory(robloxVersions)) {
            try (Stream<Path> paths = Files.list(robloxVersions)) {
                Optional<Path> latest = paths
                        .map(p -> p.resolve("RobloxPlayerBeta.exe"))
                        .filter(Files::isRegularFile)
                        .findFirst();
                if (latest.isPresent()) {
                    robloxFound = true;
                    robloxCount = 1;
                    installPaths.add(latest.get().getParent().toString());
                }
            } catch (IOException e) {
                errors.add("Roblox: " + e.getMessage());
            }
        }

        int manualCount = 0;
        if (library != null) {
            for (Game g : library.snapshot()) {
                if (g.manual()) {
                    manualCount++;
                    if (!g.folder().isBlank()) installPaths.add(g.folder());
                }
            }
        }

        if (robloxFound) {
            warnings.add("Roblox sub-detector active inside Local games (1 installation found).");
        }
        int total = manualCount + robloxCount;
        Health health = !errors.isEmpty() ? Health.ERROR : (total > 0 ? Health.SUCCESS : Health.INACTIVE);

        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        return new PlatformReport(
                "Local", health, DetectorKind.IMPLEMENTED, robloxFound || manualCount > 0, total > 0,
                searched, installPaths, List.of(),
                total, now, durationMs, warnings, errors, null, null
        );
    }

    private static PlatformReport unimplemented(String platform) {
        return new PlatformReport(
                platform, Health.INACTIVE, DetectorKind.NOT_IMPLEMENTED, false, false,
                List.of(), List.of(), List.of(),
                0, Instant.now(), 0L, List.of(), List.of(), null, null
        );
    }

    private static void addPath(Set<Path> roots, String value) {
        if (value != null && !value.isBlank()) {
            try {
                roots.add(Path.of(value).toAbsolutePath().normalize());
            } catch (InvalidPathException ignored) {
            }
        }
    }
}
