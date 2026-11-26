package br.com.gameshelf.service.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe, sanitized in-memory and rotating file logger for GameShelf diagnostics.
 * Never logs raw credentials, API keys, or unredacted personal paths.
 */
public final class AppLogger {
    public enum Level { DEBUG, INFO, WARNING, ERROR }

    public record LogEntry(Instant time, Level level, String service, String message, String details) {}

    private static final int MAX_MEMORY_ENTRIES = 500;
    private static final long DEFAULT_MAX_FILE_BYTES = 512 * 1024L;
    private static final int MAX_ARCHIVES = 2;
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final Path logsDir;
    private final Path activeFile;
    private final long maxFileBytes;
    private final Deque<LogEntry> entries = new ArrayDeque<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public AppLogger(Path dataDirectory) {
        this(dataDirectory, DEFAULT_MAX_FILE_BYTES);
    }

    public AppLogger(Path dataDirectory, long maxFileBytes) {
        this.logsDir = dataDirectory.resolve("logs");
        this.activeFile = logsDir.resolve("gameshelf.log");
        this.maxFileBytes = Math.max(1024L, maxFileBytes);
    }

    public Path logsDirectory() {
        return logsDir;
    }

    public void addListener(Runnable listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public void debug(String service, String message) {
        log(Level.DEBUG, service, message, "");
    }

    public void info(String service, String message) {
        log(Level.INFO, service, message, "");
    }

    public void warn(String service, String message) {
        log(Level.WARNING, service, message, "");
    }

    public void warn(String service, String message, String details) {
        log(Level.WARNING, service, message, details);
    }

    public void error(String service, String message, Throwable t) {
        String detail = t == null ? "" : t.getClass().getSimpleName() + ": " + Objects.toString(t.getMessage(), "");
        log(Level.ERROR, service, message, detail);
    }

    public void error(String service, String message, String details) {
        log(Level.ERROR, service, message, details);
    }

    public synchronized void log(Level level, String service, String message, String details) {
        LogEntry entry = new LogEntry(
                Instant.now(),
                level == null ? Level.INFO : level,
                service == null || service.isBlank() ? "App" : service,
                DiagnosticSanitizer.sanitize(Objects.toString(message, "")),
                DiagnosticSanitizer.sanitize(Objects.toString(details, ""))
        );
        entries.addLast(entry);
        while (entries.size() > MAX_MEMORY_ENTRIES) {
            entries.removeFirst();
        }
        appendToFile(entry);
        for (Runnable r : listeners) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }

    public synchronized List<LogEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized List<LogEntry> filter(Level levelFilter, String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return entries.stream()
                .filter(e -> levelFilter == null || e.level() == levelFilter)
                .filter(e -> q.isEmpty()
                        || e.service().toLowerCase(Locale.ROOT).contains(q)
                        || e.message().toLowerCase(Locale.ROOT).contains(q)
                        || e.details().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    private void appendToFile(LogEntry entry) {
        try {
            Files.createDirectories(logsDir);
            if (Files.exists(activeFile) && Files.size(activeFile) >= maxFileBytes) {
                rotateFiles();
            }
            String line = TS.format(entry.time()) + " [" + entry.level() + "] [" + entry.service() + "] "
                    + entry.message() + (entry.details().isBlank() ? "" : " | " + entry.details()) + System.lineSeparator();
            Files.writeString(activeFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private void rotateFiles() throws IOException {
        for (int i = MAX_ARCHIVES; i >= 1; i--) {
            Path src = i == 1 ? activeFile : logsDir.resolve("gameshelf." + (i - 1) + ".log");
            Path dst = logsDir.resolve("gameshelf." + i + ".log");
            if (Files.exists(src)) {
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
