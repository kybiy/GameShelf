package br.com.gameshelf.service.diagnostics;

import br.com.gameshelf.service.i18n.LocalizationService;
import br.com.gameshelf.service.update.AppVersion;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Complete sanitized diagnostic snapshot exportable to TXT and JSON. */
public record DiagnosticReport(
        Instant generatedAt,
        GeneralInfo general,
        List<PlatformDiagnostics.PlatformReport> platforms,
        ProcessDiagnostics.Snapshot processes,
        StorageInfo storage,
        PerformanceInfo performance,
        List<AppLogger.LogEntry> recentLogs
) {
    public record GeneralInfo(
            String appVersion,
            String javaVersion,
            String javafxVersion,
            String osName,
            String osVersion,
            String osArch,
            long uptimeMillis,
            int totalGames,
            int autoDetectedGames,
            int manualGames,
            int runningGames,
            String databaseStatus,
            String monitorStatus,
            String lastLibraryScanStatus
    ) {}

    public record StorageInfo(
            String databasePath,
            long databaseSizeBytes,
            String schemaVersion,
            int gamesCount,
            int sessionsCount,
            long imageCacheBytes,
            int imageCacheLimitMb,
            long diskFreeBytes,
            String integrityCheckResult
    ) {}

    public record PerformanceInfo(
            long jvmHeapUsedBytes,
            long jvmHeapMaxBytes,
            long processRamBytes,
            double processCpuLoad,
            int threadCount,
            long lastDetectionMs,
            long uptimeMillis
    ) {}

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    public String toSanitizedText(LocalizationService i18n, Collection<String> extraSecrets) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== GameShelf Diagnostic Report ===").append('\n');
        sb.append("Generated (UTC): ").append(ISO.format(generatedAt)).append('\n');
        sb.append("Version        : ").append(AppVersion.CURRENT).append('\n');
        sb.append("Java           : ").append(general.javaVersion()).append('\n');
        sb.append("JavaFX         : ").append(general.javafxVersion()).append('\n');
        sb.append("OS             : ").append(general.osName()).append(' ').append(general.osVersion())
                .append(" (").append(general.osArch()).append(")\n");
        sb.append("Uptime         : ").append(i18n.formatDurationClock(general.uptimeMillis() / 1000L)).append('\n');
        sb.append("Games          : total=").append(general.totalGames())
                .append(", auto=").append(general.autoDetectedGames())
                .append(", manual=").append(general.manualGames())
                .append(", running=").append(general.runningGames()).append('\n');
        sb.append("Database       : ").append(general.databaseStatus()).append('\n');
        sb.append("Monitor        : ").append(general.monitorStatus()).append('\n');
        sb.append("Last scan      : ").append(DiagnosticSanitizer.sanitize(general.lastLibraryScanStatus(), extraSecrets)).append("\n\n");

        sb.append("=== Storage & Integrity ===").append('\n');
        sb.append("Database path  : ").append(DiagnosticSanitizer.sanitize(storage.databasePath(), extraSecrets)).append('\n');
        sb.append("Database size  : ").append(storage.databaseSizeBytes()).append(" bytes\n");
        sb.append("Schema version : ").append(storage.schemaVersion()).append('\n');
        sb.append("Sessions count : ").append(storage.sessionsCount()).append('\n');
        sb.append("Cache size/lim : ").append(storage.imageCacheBytes()).append(" bytes / ")
                .append(storage.imageCacheLimitMb()).append(" MB\n");
        sb.append("Disk free      : ").append(storage.diskFreeBytes()).append(" bytes\n");
        sb.append("Integrity      : ").append(DiagnosticSanitizer.sanitize(storage.integrityCheckResult(), extraSecrets)).append("\n\n");

        sb.append("=== Performance ===").append('\n');
        sb.append("JVM Heap       : ").append(performance.jvmHeapUsedBytes()).append(" / ")
                .append(performance.jvmHeapMaxBytes()).append(" bytes\n");
        sb.append("Process RAM    : ").append(performance.processRamBytes() >= 0 ? performance.processRamBytes() + " bytes" : "Unavailable").append('\n');
        sb.append("Process CPU    : ").append(performance.processCpuLoad() >= 0 ? String.format(Locale.ROOT, "%.2f%%", performance.processCpuLoad() * 100.0) : "Unavailable").append('\n');
        sb.append("Threads        : ").append(performance.threadCount()).append('\n');
        sb.append("Detection time : ").append(performance.lastDetectionMs()).append(" ms\n\n");

        sb.append("=== Platforms ===").append('\n');
        for (PlatformDiagnostics.PlatformReport p : platforms) {
            sb.append("- ").append(p.canonicalName())
                    .append(" | health=").append(p.health())
                    .append(" | detector=").append(p.detectorKind())
                    .append(" | launcherFound=").append(p.launcherFound())
                    .append(" | games=").append(p.gamesCount())
                    .append(" | durationMs=").append(p.durationMs()).append('\n');
            for (String dir : p.searchedDirs()) {
                sb.append("    searched: ").append(DiagnosticSanitizer.sanitize(dir, extraSecrets)).append('\n');
            }
            for (String w : p.warnings()) {
                sb.append("    warning : ").append(DiagnosticSanitizer.sanitize(w, extraSecrets)).append('\n');
            }
            for (String err : p.errors()) {
                sb.append("    error   : ").append(DiagnosticSanitizer.sanitize(err, extraSecrets)).append('\n');
            }
        }
        sb.append('\n');

        sb.append("=== Process Monitoring ===").append('\n');
        sb.append("Active         : ").append(processes.monitorActive()).append(" (interval=").append(processes.intervalSeconds()).append("s)\n");
        sb.append("Scanned procs  : ").append(processes.processesScanned()).append(", accessErrors=").append(processes.accessErrors()).append('\n');
        sb.append("Active games   : ").append(processes.monitoredGames().stream().filter(ProcessDiagnostics.MonitoredGameInfo::activeSession).count()).append('\n');
        sb.append("Conflicts      : ").append(processes.conflicts().size()).append('\n');
        for (String c : processes.conflicts()) {
            sb.append("    conflict: ").append(DiagnosticSanitizer.sanitize(c, extraSecrets)).append('\n');
        }
        sb.append('\n');

        sb.append("=== Recent Logs ===").append('\n');
        for (AppLogger.LogEntry e : recentLogs) {
            sb.append(ISO.format(e.time())).append(" [").append(e.level()).append("] [").append(e.service()).append("] ")
                    .append(DiagnosticSanitizer.sanitize(e.message(), extraSecrets));
            if (!e.details().isBlank()) {
                sb.append(" | ").append(DiagnosticSanitizer.sanitize(e.details(), extraSecrets));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public String toSanitizedJson(Collection<String> extraSecrets) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"generatedAt\": ").append(q(ISO.format(generatedAt))).append(",\n");
        sb.append("  \"general\": {\n");
        sb.append("    \"appVersion\": ").append(q(general.appVersion())).append(",\n");
        sb.append("    \"javaVersion\": ").append(q(general.javaVersion())).append(",\n");
        sb.append("    \"javafxVersion\": ").append(q(general.javafxVersion())).append(",\n");
        sb.append("    \"osName\": ").append(q(general.osName())).append(",\n");
        sb.append("    \"osVersion\": ").append(q(general.osVersion())).append(",\n");
        sb.append("    \"osArch\": ").append(q(general.osArch())).append(",\n");
        sb.append("    \"uptimeMillis\": ").append(general.uptimeMillis()).append(",\n");
        sb.append("    \"totalGames\": ").append(general.totalGames()).append(",\n");
        sb.append("    \"autoDetectedGames\": ").append(general.autoDetectedGames()).append(",\n");
        sb.append("    \"manualGames\": ").append(general.manualGames()).append(",\n");
        sb.append("    \"runningGames\": ").append(general.runningGames()).append(",\n");
        sb.append("    \"databaseStatus\": ").append(q(general.databaseStatus())).append(",\n");
        sb.append("    \"monitorStatus\": ").append(q(general.monitorStatus())).append(",\n");
        sb.append("    \"lastLibraryScanStatus\": ").append(q(DiagnosticSanitizer.sanitize(general.lastLibraryScanStatus(), extraSecrets))).append("\n");
        sb.append("  },\n");

        sb.append("  \"storage\": {\n");
        sb.append("    \"databasePath\": ").append(q(DiagnosticSanitizer.sanitize(storage.databasePath(), extraSecrets))).append(",\n");
        sb.append("    \"databaseSizeBytes\": ").append(storage.databaseSizeBytes()).append(",\n");
        sb.append("    \"schemaVersion\": ").append(q(storage.schemaVersion())).append(",\n");
        sb.append("    \"gamesCount\": ").append(storage.gamesCount()).append(",\n");
        sb.append("    \"sessionsCount\": ").append(storage.sessionsCount()).append(",\n");
        sb.append("    \"imageCacheBytes\": ").append(storage.imageCacheBytes()).append(",\n");
        sb.append("    \"imageCacheLimitMb\": ").append(storage.imageCacheLimitMb()).append(",\n");
        sb.append("    \"diskFreeBytes\": ").append(storage.diskFreeBytes()).append(",\n");
        sb.append("    \"integrityCheckResult\": ").append(q(DiagnosticSanitizer.sanitize(storage.integrityCheckResult(), extraSecrets))).append("\n");
        sb.append("  },\n");

        sb.append("  \"performance\": {\n");
        sb.append("    \"jvmHeapUsedBytes\": ").append(performance.jvmHeapUsedBytes()).append(",\n");
        sb.append("    \"jvmHeapMaxBytes\": ").append(performance.jvmHeapMaxBytes()).append(",\n");
        sb.append("    \"processRamBytes\": ").append(performance.processRamBytes()).append(",\n");
        sb.append("    \"processCpuLoad\": ").append(String.format(Locale.ROOT, "%.4f", performance.processCpuLoad())).append(",\n");
        sb.append("    \"threadCount\": ").append(performance.threadCount()).append(",\n");
        sb.append("    \"lastDetectionMs\": ").append(performance.lastDetectionMs()).append("\n");
        sb.append("  },\n");

        sb.append("  \"platforms\": [\n");
        for (int i = 0; i < platforms.size(); i++) {
            var p = platforms.get(i);
            sb.append("    {\n");
            sb.append("      \"name\": ").append(q(p.canonicalName())).append(",\n");
            sb.append("      \"health\": ").append(q(p.health().name())).append(",\n");
            sb.append("      \"detectorKind\": ").append(q(p.detectorKind().name())).append(",\n");
            sb.append("      \"launcherFound\": ").append(p.launcherFound()).append(",\n");
            sb.append("      \"gamesDetected\": ").append(p.gamesDetected()).append(",\n");
            sb.append("      \"gamesCount\": ").append(p.gamesCount()).append(",\n");
            sb.append("      \"durationMs\": ").append(p.durationMs()).append(",\n");
            sb.append("      \"searchedDirs\": ").append(jsonArray(p.searchedDirs(), extraSecrets)).append(",\n");
            sb.append("      \"warnings\": ").append(jsonArray(p.warnings(), extraSecrets)).append(",\n");
            sb.append("      \"errors\": ").append(jsonArray(p.errors(), extraSecrets)).append("\n");
            sb.append("    }").append(i + 1 < platforms.size() ? "," : "").append('\n');
        }
        sb.append("  ],\n");

        sb.append("  \"processes\": {\n");
        sb.append("    \"monitorActive\": ").append(processes.monitorActive()).append(",\n");
        sb.append("    \"intervalSeconds\": ").append(processes.intervalSeconds()).append(",\n");
        sb.append("    \"processesScanned\": ").append(processes.processesScanned()).append(",\n");
        sb.append("    \"accessErrors\": ").append(processes.accessErrors()).append(",\n");
        sb.append("    \"activeSessionsCount\": ").append(processes.monitoredGames().stream().filter(ProcessDiagnostics.MonitoredGameInfo::activeSession).count()).append(",\n");
        sb.append("    \"conflicts\": ").append(jsonArray(processes.conflicts(), extraSecrets)).append("\n");
        sb.append("  },\n");

        sb.append("  \"recentLogs\": [\n");
        for (int i = 0; i < recentLogs.size(); i++) {
            var e = recentLogs.get(i);
            sb.append("    {\"time\": ").append(q(ISO.format(e.time())))
                    .append(", \"level\": ").append(q(e.level().name()))
                    .append(", \"service\": ").append(q(e.service()))
                    .append(", \"message\": ").append(q(DiagnosticSanitizer.sanitize(e.message(), extraSecrets)))
                    .append("}").append(i + 1 < recentLogs.size() ? "," : "").append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String jsonArray(List<String> items, Collection<String> extraSecrets) {
        StringJoiner j = new StringJoiner(", ", "[", "]");
        for (String item : items) j.add(q(DiagnosticSanitizer.sanitize(item, extraSecrets)));
        return j.toString();
    }

    private static String q(String s) {
        if (s == null) return "\"\"";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
