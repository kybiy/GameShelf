package br.com.gameshelf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StabilityTest {
    @TempDir Path dir;
    @Test void schemaBackupRestoreAndFutureVersion() throws Exception {
        Path legacy=dir.resolve("library.properties");
        Library lib=new Library(legacy);
        Game game=new Game("local:a","A","Local","","A.exe","",true,"",123,true);
        lib.add(game);
        if(com.sun.jna.Platform.isWindows())lib.settingValue("gridDbKey","synthetic-test-value");
        Path backup=dir.resolve("backup.db");lib.backup(backup);
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+backup);var st=c.createStatement()){
            assertFalse(st.executeQuery("SELECT * FROM setting WHERE name='gridDbKey'").next());
        }
        lib.remove(game.id());assertTrue(Files.isRegularFile(lib.restore(backup)));assertEquals(game,lib.get(game.id()));
        Path corrupt=dir.resolve("invalid.db");Files.writeString(corrupt,"invalid");
        assertThrows(java.io.IOException.class,()->lib.restore(corrupt));assertEquals(game,new Library(legacy).get(game.id()));
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+lib.databaseFile());var st=c.createStatement()){
            var r=st.executeQuery("PRAGMA user_version");assertEquals(1,r.getInt(1));r.close();st.execute("PRAGMA user_version=99");
        }
        assertThrows(java.io.IOException.class,()->new Library(legacy));
    }
    @Test void failedScanSplitsSession(){
        assertDoesNotThrow(()->{
            Library lib=new Library(dir.resolve("library.properties"));
            Game g=new Game("local:a","A","Local","","A.exe","",true,"",0,false);lib.add(g);
            var tracker=new Playtime(lib,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),()->3_000_000_000L,()->{throw new SecurityException();});
            tracker.tick(List.of("A.exe"),0);tracker.tick(List.of("A.exe"),2_000_000_000L);
            assertThrows(SecurityException.class,tracker::poll);
            tracker.tick(List.of("A.exe"),4_000_000_000L);assertEquals(2,lib.get(g.id()).seconds());assertEquals(2,lib.history(g.id()).size());
        });
    }
    @Test void ambiguousProcessAndWallClockChange() throws Exception{
        Library lib=new Library(dir.resolve("library.properties"));
        Game g=new Game("local:first","First","Local","","A.exe","",true,"",0,false);
        lib.add(g);lib.add(new Game("local:second","Second","Local","","A.exe","",true,"",0,false));
        var wall=new java.util.concurrent.atomic.AtomicLong(100);
        Clock clock=new Clock(){
            public ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(ZoneId ignored){return this;}
            public Instant instant(){return Instant.ofEpochSecond(wall.get());}
        };
        var monitor=new Playtime(lib,clock,()->0L,java.util.stream.Stream::empty);
        monitor.tick(List.of("A.exe"),0);assertTrue(monitor.sessions().isEmpty());
        lib.remove("local:second");monitor.tick(List.of("A.exe"),1_000_000_000L);
        wall.set(1000);monitor.tick(List.of("A.exe"),3_000_000_000L);
        assertEquals(2,lib.history(g.id()).size());assertEquals(0,lib.get(g.id()).seconds());
    }
    @Test void oversizedOrDeepManifestsFailSafely()throws Exception{
        Path f=dir.resolve("oversize.item");Files.write(f,new byte[8*1024*1024+1]);
        assertThrows(java.io.IOException.class,()->Detector.readManifest(f));
        assertThrows(IllegalArgumentException.class,()->ManifestParser.jsonAny("[".repeat(150)+"0"+"]".repeat(150)));
    }
    @Test void offlineBlocksExternalRequests(){
        NetworkPolicy.offline(true);
        try{assertThrows(java.io.IOException.class,NetworkPolicy::requireOnline);}finally{NetworkPolicy.offline(false);}
    }
}
