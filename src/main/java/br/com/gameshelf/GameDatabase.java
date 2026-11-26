package br.com.gameshelf;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** SQLite is opened only for a load or transaction, so a closed app has a portable database. */
final class GameDatabase {
    final Path file;
    GameDatabase(Path file) throws IOException {
        this.file = file;
        Files.createDirectories(file.toAbsolutePath().getParent());
    }
    Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
    }
    void load(Map<String, Game> games, Map<String, Session> sessions, Map<String, String> settings,
              Path legacy) throws IOException {
        try (Connection c = connect(); Statement sql = c.createStatement()) {
            c.setAutoCommit(false);
            sql.execute("CREATE TABLE IF NOT EXISTS game(id TEXT PRIMARY KEY,name TEXT,platform TEXT,folder TEXT,exe TEXT,uri TEXT,installed INTEGER,monitor TEXT,seconds INTEGER,favorite INTEGER)");
            sql.execute("CREATE TABLE IF NOT EXISTS session(id TEXT PRIMARY KEY,game_id TEXT,start INTEGER,end INTEGER,seconds INTEGER)");
            sql.execute("CREATE INDEX IF NOT EXISTS session_game ON session(game_id,start)");
            sql.execute("CREATE TABLE IF NOT EXISTS setting(name TEXT PRIMARY KEY,value TEXT)");
            try (ResultSet rows = sql.executeQuery("SELECT name,value FROM setting")) {
                while (rows.next()) settings.put(rows.getString(1), rows.getString(2));
            }
            if (!settings.containsKey("migration.v1")) {
                Properties p = new Properties();
                if (Files.exists(legacy)) try (InputStream input = Files.newInputStream(legacy)) { p.load(input); }
                int count = Integer.parseInt(p.getProperty("count", "0"));
                if (count < 0 || count > 100000) throw new IOException("Quantidade inválida na biblioteca antiga");
                try (PreparedStatement put = gameWriter(c)) {
                    for (int i = 0; i < count; i++) {
                        String k = "game." + i + ".";
                        String id = p.getProperty(k + "id");
                        if (id == null || id.isBlank() || games.containsKey(id)) throw new IOException("Identidade inválida na biblioteca antiga");
                        Game game = new Game(id, p.getProperty(k+"name","Sem nome"), p.getProperty(k+"platform","Local"),
                            p.getProperty(k+"folder",""), p.getProperty(k+"exe",""), p.getProperty(k+"uri",""),
                            Boolean.parseBoolean(p.getProperty(k+"installed","true")), p.getProperty(k+"monitor",""),
                            Math.max(0, Long.parseLong(p.getProperty(k+"seconds","0"))), Boolean.parseBoolean(p.getProperty(k+"favorite","false")));
                        games.put(id,game);
                        bindGame(put,game);
                    }
                    put.executeBatch();
                }
                sql.execute("INSERT INTO setting VALUES('migration.v1','1')");
                settings.put("migration.v1","1");
            }
            sql.executeUpdate("UPDATE game SET platform='Local' WHERE platform='Roblox'");
            try (ResultSet r = sql.executeQuery("SELECT * FROM game")) {
                while (r.next()) games.put(r.getString(1), new Game(r.getString(1),r.getString(2),r.getString(3),
                    r.getString(4),r.getString(5),r.getString(6),r.getBoolean(7),r.getString(8),r.getLong(9),r.getBoolean(10)));
            }
            try (ResultSet r = sql.executeQuery("SELECT * FROM session ORDER BY start")) {
                while (r.next()) sessions.put(r.getString(1),new Session(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4),r.getLong(5)));
            }
            c.commit();
        } catch (SQLException | IllegalArgumentException e) { throw new IOException("Falha ao ler a biblioteca",e); }
    }
    private static PreparedStatement gameWriter(Connection c) throws SQLException {
        return c.prepareStatement("INSERT OR REPLACE INTO game VALUES(?,?,?,?,?,?,?,?,?,?)");
    }
    private static void bindGame(PreparedStatement p, Game g) throws SQLException {
        p.setString(1,g.id()); p.setString(2,g.name()); p.setString(3,g.platform()); p.setString(4,g.folder());
        p.setString(5,g.executable()); p.setString(6,g.launchUri()); p.setBoolean(7,g.installed());
        p.setString(8,g.monitorOverride()); p.setLong(9,g.seconds()); p.setBoolean(10,g.favorite()); p.addBatch();
    }
    void save(Collection<Game> games, Collection<String> removed, Collection<Session> sessions,
              Map<String,String> settings) throws IOException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (var game = gameWriter(c);
                 var drop = c.prepareStatement("DELETE FROM game WHERE id=?");
                 var history = c.prepareStatement("DELETE FROM session WHERE game_id=?");
                 var session = c.prepareStatement("INSERT OR REPLACE INTO session VALUES(?,?,?,?,?)");
                 var setting = c.prepareStatement("INSERT OR REPLACE INTO setting VALUES(?,?)")) {
                for (String id : removed) {
                    drop.setString(1,id); drop.addBatch();
                    history.setString(1,id); history.addBatch();
                }
                drop.executeBatch(); history.executeBatch();
                for (Game g : games) bindGame(game,g);
                game.executeBatch();
                for (Session s : sessions) {
                    session.setString(1,s.id()); session.setString(2,s.gameId()); session.setLong(3,s.start());
                    session.setLong(4,s.end()); session.setLong(5,s.seconds()); session.addBatch();
                }
                session.executeBatch();
                for (var entry : settings.entrySet()) {
                    setting.setString(1,entry.getKey()); setting.setString(2,entry.getValue()); setting.addBatch();
                }
                setting.executeBatch();
                c.commit();
            }
        } catch (SQLException e) { throw new IOException("Falha ao salvar a biblioteca",e); }
    }
}
