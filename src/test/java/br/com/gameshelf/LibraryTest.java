package br.com.gameshelf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class LibraryTest {
    @TempDir Path dir;
    @Test void migrationPreservesOriginalAndDoesNotInventSessions() throws Exception {
        Path source=dir.resolve("library.properties");
        String legacy="count=1\ngame.0.id=steam:42\ngame.0.name=Original\ngame.0.platform=Steam\ngame.0.seconds=7123\ngame.0.favorite=true\ngame.0.monitor=D:/Games/Real.exe\n";
        Files.writeString(source,legacy);Library library=new Library(source);
        Game game=library.get("steam:42");assertEquals(7123,game.seconds());assertTrue(game.favorite());
        assertEquals("D:/Games/Real.exe",game.monitorOverride());assertTrue(library.history(null).isEmpty());
        library.favorite(game.id());library.save();
        assertFalse(new Library(source).get(game.id()).favorite());assertEquals(legacy,Files.readString(source));
    }
    @Test void brokenMigrationCanBeCorrectedAndRetried() throws Exception {
        Path source=dir.resolve("library.properties");
        Files.writeString(source,"count=2\ngame.0.id=steam:1\n");
        assertThrows(IOException.class,()->new Library(source));
        Files.writeString(source,"count=1\ngame.0.id=steam:2\n");
        Library retry=new Library(source);assertNull(retry.get("steam:1"));assertNotNull(retry.get("steam:2"));
    }
    @Test void checkpointsRestoreBackupsAndRemoveSessions() throws Exception {
        Path source=dir.resolve("library.properties");Library lib=new Library(source);
        Game game=new Game("local:1","Jogo","GOG","","X:/Games/Test.exe","",true,"",300,false);
        lib.add(game);Playtime tracker=new Playtime(lib);
        tracker.tick(List.of(game.executable()),0);
        tracker.tick(List.of(game.executable()),2_000_000_000L);lib.save();
        Library reopened=new Library(source);assertEquals(302,reopened.get(game.id()).seconds());
        assertEquals(2,reopened.history(game.id()).getFirst().seconds());assertTrue(new Playtime(reopened).sessions().isEmpty());
        tracker.tick(List.of(game.executable()),60_000_000_000L);
        assertEquals(2,lib.history(game.id()).size());assertEquals(302,lib.get(game.id()).seconds());
        Path backup=dir.resolve("backup/library.db");Files.createDirectories(backup.getParent());lib.backup(backup);
        assertEquals(302,new Library(backup.resolveSibling("library.properties")).get(game.id()).seconds());
        lib.remove(game.id());lib.save();Library empty=new Library(source);
        assertTrue(empty.snapshot().isEmpty());assertTrue(empty.history(null).isEmpty());
    }
    @Test void robloxKeepsItsIdentityWhenMovedToLocal() throws Exception {
        Path source=dir.resolve("library.properties"),versions=dir.resolve("Versions");
        Path build=Files.createDirectories(versions.resolve("version-1"));
        Files.createFile(build.resolve("RobloxPlayerBeta.exe"));
        Game detected=Detector.roblox(versions).getFirst();
        assertEquals("Local",detected.platform());assertFalse(detected.manual());
        Library old=new Library(source);
        old.add(new Game(detected.id(),"Roblox","Roblox",detected.folder(),detected.executable(),detected.launchUri(),true,"",100,true));
        Session session=new Session("s",detected.id(),10,20,10);
        old.checkpoint(session,10);old.settingValue("cover."+detected.id(),"cover.png");old.save();
        Library migrated=new Library(source);
        assertEquals("Local",migrated.get(detected.id()).platform());
        migrated.merge(List.of(detected),true);migrated.merge(List.of(detected),true);migrated.save();
        Library reopened=new Library(source);Game game=reopened.snapshot().getFirst();
        assertEquals(1,reopened.snapshot().size());assertEquals(110,game.seconds());assertTrue(game.favorite());
        assertEquals("cover.png",reopened.setting("cover."+game.id(),""));assertEquals(List.of(session),reopened.history(game.id()));
        assertEquals("https://www.roblox.com/games",game.launchUri());
    }
    @Test void overlappingSessionsAndDayBoundaries() {
        var sessions=List.of(new Session("a","a",90,125,35),new Session("b","b",118,160,42));
        Instant start=Instant.ofEpochSecond(100),end=Instant.ofEpochSecond(150);
        assertEquals(50,new Activity(sessions,true).between(start,end));
        assertEquals(57,new Activity(sessions,false).between(start,end));
        assertEquals(25,sessions.getFirst().inPeriod(start,end));
    }
}
