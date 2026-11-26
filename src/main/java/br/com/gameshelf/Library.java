package br.com.gameshelf;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class Library {
    private final Map<String,Game> games = new LinkedHashMap<>();
    private final Map<String,Session> sessions = new LinkedHashMap<>();
    private final Map<String,String> settings = new HashMap<>();
    private final Set<String> changed = new HashSet<>(), removed = new HashSet<>();
    private final Map<String,Session> checkpoints = new HashMap<>();
    private final Map<String,String> preferences = new HashMap<>();
    private final GameDatabase database;
    private volatile Instant lastSavedAt;

    public Library(Path legacy) throws IOException {
        database = new GameDatabase(legacy.resolveSibling("library.db"));
        database.load(games,sessions,settings,legacy);
    }
    public Path databaseFile() { return database.file; }
    public String schemaVersion() { return setting("migration.v1", "1").equals("1") ? "v1" : setting("migration.v1", "v1"); }
    public String checkDatabaseIntegrity() { synchronized (database) { return database.checkIntegrity(); } }
    public Instant lastSavedAt() { return lastSavedAt; }
    public synchronized int sessionsCount() { return sessions.size(); }
    public synchronized List<Game> snapshot() { return List.copyOf(games.values()); }
    public synchronized Game get(String id) { return games.get(id); }
    public synchronized List<Session> history(String gameId) {
        return sessions.values().stream().filter(s -> gameId == null || s.gameId().equals(gameId))
            .sorted(Comparator.comparingLong(Session::start).reversed()).toList();
    }
    public synchronized String setting(String key,String fallback) { return settings.getOrDefault(key,fallback); }
    public synchronized void settingValue(String key,String value) { settings.put(key,value); preferences.put(key,value); }
    public synchronized void add(Game g) {
        if (!games.containsKey(g.id())) settingValue("added."+g.id(),Long.toString(Instant.now().getEpochSecond()));
        if (!g.equals(games.put(g.id(),g))) changed.add(g.id());
    }
    public synchronized void merge(List<Game> found,boolean complete) {
        Set<String> ids = new HashSet<>();
        for (Game game : found) {
            ids.add(game.id());
            Game old = games.get(game.id());
            add(old == null ? game : game.preserve(old));
        }
        if (complete) for (Game g : snapshot()) if (!ids.contains(g.id())) {
            add(g.withInstalled(g.manual() && Files.isRegularFile(Path.of(g.executable()))));
        }
    }
    public synchronized void checkpoint(Session session,long delta) {
        Game g = games.get(session.gameId());
        if (g == null) return;
        if (delta > 0) add(g.withSeconds(g.seconds()+delta));
        sessions.put(session.id(),session);
        checkpoints.put(session.id(),session);
    }
    public synchronized void favorite(String id) { Game g=get(id); if(g!=null)add(g.withFavorite(!g.favorite())); }
    public synchronized void monitor(String id,String exe) { Game g=get(id); if(g!=null)add(g.withMonitor(exe)); }
    public synchronized void remove(String id) {
        games.remove(id); changed.remove(id); removed.add(id);
        sessions.values().removeIf(s -> s.gameId().equals(id));
        checkpoints.values().removeIf(s -> s.gameId().equals(id));
    }
    public void backup(Path destination) throws IOException {
        synchronized(database) {copyBackup(destination);}
    }
    private void copyBackup(Path destination) throws IOException {
        if (Files.exists(destination) && Files.isSameFile(database.file,destination))
            throw new IOException("Escolha outro arquivo para o backup.");
        save();
        Path tmp=Files.createTempFile(destination.toAbsolutePath().getParent(),"gameshelf-backup-",".tmp");
        try {
            Files.copy(database.file,tmp,StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp,destination,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(tmp);}
    }
    public void save() throws IOException {
        synchronized(database) {
            List<Game> updates;
            Set<String> deletes;
            Map<String,Session> history;
            Map<String,String> config;
            synchronized(this) {
                if(changed.isEmpty()&&removed.isEmpty()&&checkpoints.isEmpty()&&preferences.isEmpty())return;
                updates=changed.stream().map(games::get).toList();deletes=Set.copyOf(removed);
                history=Map.copyOf(checkpoints);config=Map.copyOf(preferences);
                changed.clear();removed.clear();checkpoints.clear();preferences.clear();
            }
            try {database.save(updates,deletes,history.values(),config);lastSavedAt=Instant.now();}
            catch(IOException e){
                synchronized(this){
                    for(Game g:updates)if(games.containsKey(g.id()))changed.add(g.id());
                    removed.addAll(deletes);
                    history.forEach((key,value)->{if(sessions.containsKey(key))checkpoints.putIfAbsent(key,value);});
                    config.forEach(preferences::putIfAbsent);
                }
                throw e;
            }
        }
    }
}
