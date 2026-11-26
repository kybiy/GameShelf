package br.com.gameshelf;

import java.nio.file.*;
import java.util.*;

/** Testes sem JUnit: execute com java -ea (ou pelos scripts). */
public final class SmokeTest {
    @org.junit.jupiter.api.Test void existingChecks() throws Exception {main(new String[0]);}
    private static int checks=0;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
    public static void main(String[] args)throws Exception{
        Path tmp=Files.createTempDirectory("gameshelf-test-");try{
            Path root=tmp.resolve("Steam"), secondary=tmp.resolve("Second drive");Files.createDirectories(root.resolve("steamapps"));Path folder=secondary.resolve("steamapps/common/Game One");Files.createDirectories(folder);Files.writeString(root.resolve("steamapps/libraryfolders.vdf"),"\"libraryfolders\" { \"1\" { \"path\" \""+escape(secondary.toString())+"\" \"apps\" { \"123\" \"1\" } } }");
            Files.writeString(secondary.resolve("steamapps/appmanifest_123.acf"),"\"AppState\" { \"appid\" \"123\" \"name\" \"Game One\" \"installdir\" \"Game One\" \"StateFlags\" \"4\" }");
            List<String>warnings=new ArrayList<>();List<Game> steam=Detector.steam(root,warnings::add);check(steam.size()==1,"Steam disco secundário");check(steam.get(0).id().equals("steam:123"),"Steam id");check(warnings.isEmpty(),"Sem avisos Steam");check(steam.get(0).folder().equals(folder.toString()),"Pasta correta");
            Path manifests=tmp.resolve("EpicManifests"), epic=tmp.resolve("Epic Game");Files.createDirectories(manifests);Files.createDirectories(epic);Path epicExe=Files.createFile(epic.resolve("Game.exe"));
            Files.writeString(manifests.resolve("game.item"),"{\"AppName\":\"game-id\",\"DisplayName\":\"Epic \\u00e9\",\"InstallLocation\":\""+escape(epic.toString())+"\",\"LaunchExecutable\":\"Game.exe\",\"bIsIncompleteInstall\":false}");
            Files.writeString(manifests.resolve("partial.item"),"{\"AppName\":\"partial\",\"DisplayName\":\"Incomplete\",\"InstallLocation\":\""+escape(epic.toString())+"\",\"LaunchExecutable\":\"Game.exe\",\"bIsIncompleteInstall\":true}");
            List<Game> ep=Detector.epic(manifests,warnings::add);check(ep.size()==1,"Epic ignora incompletos");check(ep.get(0).name().equals("Epic é"),"Unicode JSON");check(ep.get(0).executable().equals(epicExe.toString()),"Epic exe");
            Path versions=tmp.resolve("Roblox/Versions"), version=versions.resolve("version-123");Files.createDirectories(version);Path rob=Files.createFile(version.resolve("RobloxPlayerBeta.exe"));Game r=Detector.roblox(versions).get(0);check(r.id().equals("roblox:player"),"Roblox ID estável");check(Playtime.matches(r,rob.toString()),"Roblox processo");check(Playtime.matches(r,versions.resolve("version-new/RobloxPlayerBeta.exe").toString()),"Roblox atualização");check(!Playtime.matches(r,version.resolve("RobloxStudioBeta.exe").toString()),"Ignorar Studio");
            Path data=tmp.resolve("data/library.properties");Library lib=new Library(data);lib.merge(List.of(steam.get(0),ep.get(0),r),true);Game s=steam.get(0);String exe=folder.resolve("Game.exe").toString();check(!Playtime.matches(s,folder.resolve("crashreporter.exe").toString()),"Ignorar crash reporter");check(!Playtime.matches(s,folder+"Other/Game.exe"),"Limite da pasta");
            Playtime tracker=new Playtime(lib);tracker.tick(List.of(exe),0);tracker.tick(List.of(exe,exe,epicExe.toString()),2_000_000_000L);check(lib.get(s.id()).seconds()==2,"Não duplica processos");check(lib.get(ep.get(0).id()).seconds()==0,"Sessão nova");tracker.tick(List.of(exe,epicExe.toString()),4_000_000_000L);check(lib.get(s.id()).seconds()==4&&lib.get(ep.get(0).id()).seconds()==2,"Jogos simultâneos");tracker.tick(List.of(exe),100_000_000_000L);check(lib.get(s.id()).seconds()==4,"Ignora lacuna longa");tracker.tick(List.of(),102_000_000_000L);check(tracker.sessions().isEmpty(),"Sessão encerrada");
            lib.favorite(s.id());lib.monitor(s.id(),exe);lib.merge(List.of(ep.get(0)),true);check(!lib.get(s.id()).installed()&&lib.get(s.id()).seconds()==4,"Desinstalação mantém horas");lib.merge(List.of(s),true);check(lib.get(s.id()).installed()&&lib.get(s.id()).favorite()&&lib.get(s.id()).monitorOverride().equals(exe),"Reinstalação preserva dados");lib.save();Library reopened=new Library(data);check(reopened.get(s.id()).seconds()==4,"Persistência");check(reopened.get(s.id()).favorite(),"Favorito persistente");check(!Playtime.matches(reopened.get(s.id()),folder.resolve("Other.exe").toString()),"EXE manual exato");
            System.out.println("OK: "+checks+" verificações aprovadas.");
        }finally{try(var walk=Files.walk(tmp)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
