package br.com.gameshelf;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/** A contagem é por jogo, não por processo: dois processos do mesmo jogo não dobram as horas. */
public final class Playtime {
    private final Library library;
    private final java.time.Clock clock;
    private final java.util.function.LongSupplier nanos;
    private final java.util.function.Supplier<Stream<ProcessHandle>> processes;
    private final Map<String, Long> remainder = new HashMap<>();
    private Map<String, Long> sessions = new HashMap<>();
    private final Map<String, Session> observed = new HashMap<>();
    private Map<String, List<Long>> matchedPids = Map.of();
    private record Identity(long pid,Optional<Instant> start){}
    private Map<String,Set<Identity>> identities=Map.of();
    private long lastWall=Long.MIN_VALUE;
    private volatile long scanNanos,scanCount;
    public double meanScanMillis(){return scanCount==0?0:(scanNanos/1e6)/scanCount;}
    private long lastNano = -1;
    private volatile int lastProcessesScanned;
    private volatile int lastAccessErrors;
    private volatile Instant lastPollAt;

    public Playtime(Library library) {
        this(library,java.time.Clock.systemUTC(),System::nanoTime,ProcessHandle::allProcesses);
    }
    Playtime(Library library,java.time.Clock clock,java.util.function.LongSupplier nanos,
             java.util.function.Supplier<Stream<ProcessHandle>> processes) {
        this.library = library;this.clock=clock;this.nanos=nanos;this.processes=processes;
    }

    public synchronized Map<String, Long> sessions() {
        return Map.copyOf(sessions);
    }

    public synchronized Map<String, List<Long>> matchedPids() {
        return matchedPids;
    }

    public int lastProcessesScanned() {
        return lastProcessesScanned;
    }

    public int lastAccessErrors() {
        return lastAccessErrors;
    }

    public Instant lastPollAt() {
        return lastPollAt;
    }

    public synchronized void poll() {
        long started=nanos.getAsLong();
        record Proc(Identity identity, String command) {}
        List<Proc> procs = new ArrayList<>();
        int errors = 0;
        try (Stream<ProcessHandle> processes = this.processes.get()) {
            for (ProcessHandle p : processes.toList()) {
                try {
                    var info=p.info();var command=info.command();
                    if(command.isPresent())procs.add(new Proc(new Identity(p.pid(),info.startInstant()),command.get()));else errors++;
                } catch (SecurityException ignored) {
                    errors++;
                }
            }
        } catch(RuntimeException failed){tick(List.of(),nanos.getAsLong());synchronized(this){matchedPids=Map.of();}throw failed;}
        lastProcessesScanned = procs.size()+errors;
        lastAccessErrors = errors;
        lastPollAt = clock.instant();

        Map<String,Set<Identity>> currentIdentities=new HashMap<>();
        Map<String, List<Long>> pids = new HashMap<>();
        List<Game> snapshot = library.snapshot();
        for (Proc proc : procs) {
            Game g=uniqueMatch(snapshot,proc.command());
            if(g!=null){
                    pids.computeIfAbsent(g.id(), k -> new ArrayList<>()).add(proc.identity().pid());
                    currentIdentities.computeIfAbsent(g.id(),k->new HashSet<>()).add(proc.identity());
            }
        }
        Map<String, List<Long>> frozenPids = new HashMap<>();
        pids.forEach((k, v) -> frozenPids.put(k, List.copyOf(v)));
        synchronized (this) {
            matchedPids = Map.copyOf(frozenPids);
        }
        currentIdentities.forEach((id,now)->{
            Set<Identity> prior=identities.get(id);
            if(prior!=null && Collections.disjoint(prior,now)){observed.remove(id);remainder.remove(id);}
        });
        identities=currentIdentities;
        long finished=nanos.getAsLong();advance(pids.keySet(),finished);
        scanNanos+=Math.max(0,finished-started);scanCount++;
    }

    synchronized void tick(List<String> commands, long now) {
        Set<String> current = new HashSet<>();
        var games=library.snapshot();
        for(String command:commands){Game game=uniqueMatch(games,command);if(game!=null)current.add(game.id());}
        advance(current,now);
    }
    private synchronized void advance(Set<String> current,long now){
        long delta = lastNano == -1 ? 0 : now - lastNano;
        lastNano = now;
        // Longas lacunas (suspensão, travamento) não são creditadas.
        long wall=clock.instant().getEpochSecond();
        boolean wallGap=lastWall!=Long.MIN_VALUE && (wall-lastWall>10 || wall<lastWall);
        lastWall=wall;
        if (wallGap || delta < 0 || delta > 10_000_000_000L) {
            observed.clear();
            remainder.clear();
            delta = 0;
        }
        Map<String, Long> next = new HashMap<>();
        for (String id : current) {
            Session active = observed.get(id);
            long seconds = 0;
            if (active == null) {
                active = new Session(UUID.randomUUID().toString(), id, wall, wall, 0);
            } else {
                long total = remainder.getOrDefault(id, 0L) + delta;
                seconds = total / 1_000_000_000L;
                remainder.put(id, total % 1_000_000_000L);
                active = new Session(active.id(), id, active.start(), active.end() + seconds, active.seconds() + seconds);
            }
            library.checkpoint(active, seconds);
            observed.put(id, active);
            next.put(id, active.seconds());
        }
        observed.keySet().retainAll(current);
        remainder.keySet().retainAll(current);
        sessions = next;
    }

    synchronized java.nio.file.Path restore(java.nio.file.Path source)throws java.io.IOException{
        var path=library.restore(source);tick(List.of(),nanos.getAsLong());matchedPids=Map.of();return path;
    }
    private static String normalize(String s) {
        return s.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static Game uniqueMatch(List<Game> games,String command){
        Game found=null;
        for(Game game:games)if(game.installed()&&matches(game,command)){
            if(found!=null)return null;
            found=game;
        }
        return found;
    }

    public static boolean matches(Game g, String command) {
        String p = normalize(command), root = normalize(g.folder()), exe = normalize(g.executable());
        String name = p.substring(p.lastIndexOf('/') + 1);
        if (!name.endsWith(".exe")) return false;
        if (!g.monitorOverride().isBlank()) return p.equals(normalize(g.monitorOverride()));
        if (g.robloxPlayer()) return !root.isBlank() && p.startsWith(root + "/") && name.equals("robloxplayerbeta.exe");
        if (g.manual() || g.platform().equals("Riot Games")) return !exe.isBlank() && p.equals(exe);
        if (name.matches(".*(crash|reporter|unins|uninstall|setup|installer|redist|launcher|bootstrap|anticheat|anti-cheat|easyanticheat|battleye|cefsubprocess|updater|prereq).*"))
            return false;
        if (!exe.isBlank() && p.equals(exe)) return true;
        // Steam e Epic: aproximação por pasta, com opção de substituir por EXE exato.
        return !root.isBlank() && p.startsWith(root + "/");
    }

    public static String format(long seconds) {
        return String.format("%dh %02dm %02ds", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
