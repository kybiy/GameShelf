package br.com.gameshelf.service.diagnostics;

import br.com.gameshelf.Game;
import br.com.gameshelf.Library;
import br.com.gameshelf.Playtime;
import br.com.gameshelf.service.update.AppVersion;
import br.com.gameshelf.ui.Artwork;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Coordinates system, platform, process, storage, and performance diagnostics. */
public final class DiagnosticsService {
    private final Library library;
    private final Playtime tracker;
    private final Artwork artwork;
    private final AppLogger logger;
    private final List<String> canonicalPlatforms;

    private volatile List<PlatformDiagnostics.PlatformReport> cachedPlatforms = List.of();
    private volatile String lastIntegrityResult = "";
    private volatile long lastDetectionDurationMs;

    public DiagnosticsService(Library library, Playtime tracker, Artwork artwork,
                              AppLogger logger, List<String> canonicalPlatforms) {
        this.library = library;
        this.tracker = tracker;
        this.artwork = artwork;
        this.logger = logger;
        this.canonicalPlatforms = List.copyOf(canonicalPlatforms);
    }

    public List<PlatformDiagnostics.PlatformReport> runPlatformCheck() {
        logger.info("Detector", "Running platform diagnostics check");
        long t0 = System.nanoTime();
        List<PlatformDiagnostics.PlatformReport> reports = PlatformDiagnostics.inspectAll(library, canonicalPlatforms);
        lastDetectionDurationMs = (System.nanoTime() - t0) / 1_000_000L;
        cachedPlatforms = reports;
        for (var r : reports) {
            for (String w : r.warnings()) logger.warn(r.canonicalName(), w);
            for (String err : r.errors()) logger.error(r.canonicalName(), "Detection error", err);
        }
        logger.info("Detector", "Platform diagnostics completed in " + lastDetectionDurationMs + " ms");
        return reports;
    }

    public String runIntegrityCheck() {
        logger.info("Database", "Running non-destructive SQLite quick_check");
        String result = library.checkDatabaseIntegrity();
        lastIntegrityResult = result;
        if ("ok".equalsIgnoreCase(result)) {
            logger.info("Database", "SQLite integrity check passed: ok");
        } else {
            logger.warn("Database", "SQLite integrity check returned: " + result);
        }
        return result;
    }

    public DiagnosticReport runFullDiagnostics(boolean monitorActive, String lastScanStatus) {
        runPlatformCheck();
        runIntegrityCheck();
        return snapshot(monitorActive, lastScanStatus);
    }

    public DiagnosticReport snapshot(boolean monitorActive, String lastScanStatus) {
        if (cachedPlatforms.isEmpty()) {
            cachedPlatforms = PlatformDiagnostics.inspectAll(library, canonicalPlatforms);
        }
        if (lastIntegrityResult.isBlank()) {
            lastIntegrityResult = library.checkDatabaseIntegrity();
        }

        List<Game> games = library.snapshot();
        int totalGames = games.size();
        int manualGames = (int) games.stream().filter(Game::manual).count();
        int autoGames = totalGames - manualGames;
        int runningGames = tracker.sessions().size();

        DiagnosticReport.GeneralInfo general = new DiagnosticReport.GeneralInfo(
                AppVersion.CURRENT,
                System.getProperty("java.version", "?"),
                System.getProperty("javafx.runtime.version", System.getProperty("javafx.version", "21")),
                System.getProperty("os.name", "?"),
                System.getProperty("os.version", "?"),
                System.getProperty("os.arch", "?"),
                ManagementFactory.getRuntimeMXBean().getUptime(),
                totalGames,
                autoGames,
                manualGames,
                runningGames,
                "ok".equalsIgnoreCase(lastIntegrityResult) ? "operational" : lastIntegrityResult,
                monitorActive ? "active" : "unavailable",
                lastScanStatus == null ? "" : lastScanStatus
        );

        ProcessDiagnostics.Snapshot procs = ProcessDiagnostics.capture(library, tracker, monitorActive);

        Path dbFile = library.databaseFile();
        long dbSize = safeSize(dbFile);
        long diskFree = safeDiskFree(dbFile);
        DiagnosticReport.StorageInfo storage = new DiagnosticReport.StorageInfo(
                dbFile.toString(),
                dbSize,
                library.schemaVersion(),
                totalGames,
                library.sessionsCount(),
                artwork.cacheSizeBytes(),
                artwork.limitMb(),
                diskFree,
                lastIntegrityResult
        );

        DiagnosticReport.PerformanceInfo perf = measurePerformance();

        return new DiagnosticReport(
                Instant.now(),
                general,
                cachedPlatforms,
                procs,
                storage,
                perf,
                logger.entries()
        );
    }

    public DiagnosticReport.PerformanceInfo measurePerformance() {
        Runtime rt = Runtime.getRuntime();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        long heapMax = rt.maxMemory();
        long processRam = readWindowsWorkingSet();
        double cpuLoad = readProcessCpuLoad();
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        return new DiagnosticReport.PerformanceInfo(heapUsed, heapMax, processRam, cpuLoad, threads, lastDetectionDurationMs, uptime);
    }

    private interface WinPsapi extends StdCallLibrary {
        WinPsapi INSTANCE = Native.load("psapi", WinPsapi.class);
        boolean GetProcessMemoryInfo(WinNT.HANDLE process, ProcessMemoryCounters counters, int cb);
    }

    @Structure.FieldOrder({"cb", "PageFaultCount", "PeakWorkingSetSize", "WorkingSetSize",
            "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage",
            "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage"})
    public static class ProcessMemoryCounters extends Structure {
        public int cb;
        public int PageFaultCount;
        public BaseTSD.SIZE_T PeakWorkingSetSize;
        public BaseTSD.SIZE_T WorkingSetSize;
        public BaseTSD.SIZE_T QuotaPeakPagedPoolUsage;
        public BaseTSD.SIZE_T QuotaPagedPoolUsage;
        public BaseTSD.SIZE_T QuotaPeakNonPagedPoolUsage;
        public BaseTSD.SIZE_T QuotaNonPagedPoolUsage;
        public BaseTSD.SIZE_T PagefileUsage;
        public BaseTSD.SIZE_T PeakPagefileUsage;
    }

    private static long readWindowsWorkingSet() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) return -1L;
        try {
            var counters = new ProcessMemoryCounters();
            counters.cb = counters.size();
            if (WinPsapi.INSTANCE.GetProcessMemoryInfo(Kernel32.INSTANCE.GetCurrentProcess(), counters, counters.cb)) {
                return counters.WorkingSetSize.longValue();
            }
        } catch (Throwable ignored) {
        }
        return -1L;
    }

    private static double readProcessCpuLoad() {
        try {
            var bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                double load = sunBean.getProcessCpuLoad();
                return load >= 0.0 ? load : -1.0;
            }
        } catch (Throwable ignored) {
        }
        return -1.0;
    }

    private static long safeSize(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : 0L;
        } catch (Exception e) {
            return -1L;
        }
    }

    private static long safeDiskFree(Path file) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            return dir != null ? Files.getFileStore(dir).getUsableSpace() : -1L;
        } catch (Exception e) {
            return -1L;
        }
    }
}
