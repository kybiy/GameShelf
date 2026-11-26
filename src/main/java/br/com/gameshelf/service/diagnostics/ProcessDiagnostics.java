package br.com.gameshelf.service.diagnostics;

import br.com.gameshelf.Game;
import br.com.gameshelf.Library;
import br.com.gameshelf.Playtime;
import br.com.gameshelf.Session;

import java.time.Instant;
import java.util.*;

/** Read-only snapshot of background playtime tracking and potential executable conflicts. */
public final class ProcessDiagnostics {
    public record MonitoredGameInfo(
            String id,
            String name,
            String platform,
            String targetExecutable,
            List<Long> pids,
            boolean activeSession,
            Instant sessionStart
    ) {}

    public record Snapshot(
            boolean monitorActive,
            int intervalSeconds,
            int processesScanned,
            int accessErrors,
            Instant lastPollAt,
            Instant lastSaveAt,
            List<MonitoredGameInfo> monitoredGames,
            List<String> conflicts
    ) {}

    private ProcessDiagnostics() {}

    public static Snapshot capture(Library library, Playtime tracker, boolean monitorActive) {
        List<Game> games = library.snapshot();
        Map<String, Long> activeSessions = tracker.sessions();
        Map<String, List<Long>> matchedPids = tracker.matchedPids();

        Map<String, Instant> activeStartByGame = new HashMap<>();
        for (String gameId : activeSessions.keySet()) {
            List<Session> history = library.history(gameId);
            if (!history.isEmpty()) {
                activeStartByGame.put(gameId, Instant.ofEpochSecond(history.getFirst().start()));
            }
        }

        List<MonitoredGameInfo> monitored = new ArrayList<>();
        Map<String, List<String>> exeOwners = new HashMap<>();

        for (Game g : games) {
            if (!g.installed()) continue;
            String target = !g.monitorOverride().isBlank() ? g.monitorOverride()
                    : !g.executable().isBlank() ? g.executable()
                    : g.folder();
            String norm = target.replace('\\', '/').toLowerCase(Locale.ROOT);
            if (!norm.isBlank()) {
                exeOwners.computeIfAbsent(norm, k -> new ArrayList<>()).add(g.name());
            }
            monitored.add(new MonitoredGameInfo(
                    g.id(),
                    g.name(),
                    g.platform(),
                    target,
                    matchedPids.getOrDefault(g.id(), List.of()),
                    activeSessions.containsKey(g.id()),
                    activeStartByGame.get(g.id())
            ));
        }

        List<String> conflicts = new ArrayList<>();
        exeOwners.forEach((path, owners) -> {
            if (owners.size() > 1) {
                conflicts.add(String.join(", ", owners) + " -> " + path);
            }
        });

        return new Snapshot(
                monitorActive,
                2,
                tracker.lastProcessesScanned(),
                tracker.lastAccessErrors(),
                tracker.lastPollAt(),
                library.lastSavedAt(),
                List.copyOf(monitored),
                List.copyOf(conflicts)
        );
    }
}
