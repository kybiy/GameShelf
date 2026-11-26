package br.com.gameshelf;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Cada detector pode ser expandido sem alterar a interface ou o contador. */
public final class Detector {
    private Detector() {}
    public record Result(List<Game> games, List<String> warnings) {}
    public static Result scan() {
        List<Game> out=new ArrayList<>();List<String> errors=new ArrayList<>();Consumer<String> warn=errors::add;
        Set<Path> roots=new LinkedHashSet<>();
        String reg=registryValue("HKCU\\Software\\Valve\\Steam","SteamPath"); add(roots,reg);
        add(roots,registryValue("HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam","InstallPath"));
        add(roots,env("ProgramFiles(x86)","Steam"));add(roots,env("ProgramFiles","Steam"));
        for(Path root:roots) try {out.addAll(steam(root,warn));}catch(Exception e){warn.accept("Steam: "+e.getMessage());}
        try {out.addAll(epic(Path.of(env("ProgramData","Epic/EpicGamesLauncher/Data/Manifests")),warn));}catch(Exception e){warn.accept("Epic: "+e.getMessage());}
        try {out.addAll(roblox(Path.of(env("LOCALAPPDATA","Roblox/Versions"))));}catch(Exception e){warn.accept("Roblox: "+e.getMessage());}
        try {out.addAll(riot());}catch(Exception e){warn.accept("Riot: "+e.getMessage());}
        Map<String,Game> unique=new LinkedHashMap<>();out.forEach(g->unique.putIfAbsent(g.id(),g));return new Result(List.copyOf(unique.values()),List.copyOf(errors));
    }
    public static String env(String key,String suffix){String base=System.getenv(key);return base==null||base.isBlank()?Path.of(System.getProperty("user.home"),".gameshelf-nonexistent",key,suffix).toString():Path.of(base,suffix).toString();}
    public static String registryValue(String key,String name){if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows"))return "";try{Process p=new ProcessBuilder("reg","query",key,"/v",name).redirectErrorStream(true).start();if(!p.waitFor(3,TimeUnit.SECONDS)){p.destroyForcibly();return "";}String output=new String(p.getInputStream().readAllBytes(),java.nio.charset.Charset.defaultCharset());for(String line:output.split("\\R")){String[] parts=line.trim().split("\\s{2,}",3);if(parts.length==3&&parts[0].equalsIgnoreCase(name))return parts[2].trim();}}catch(Exception ignored){}return "";}
    private static void add(Set<Path> roots,String value){if(value!=null&&!value.isBlank())try{roots.add(Path.of(value).toAbsolutePath().normalize());}catch(InvalidPathException ignored){}}
    static List<Game> steam(Path root,Consumer<String> warn) throws IOException {
        Set<Path> libs=new LinkedHashSet<>();libs.add(root);
        for(Path vdf:List.of(root.resolve("steamapps/libraryfolders.vdf"),root.resolve("config/libraryfolders.vdf"))) {
            if(!Files.isRegularFile(vdf))continue;
            try {Map<String,Object> data=ManifestParser.map(ManifestParser.vdf(Files.readString(vdf)).get("libraryfolders"));for(var e:data.entrySet())if(e.getKey().matches("\\d+")){Object v=e.getValue();add(libs,v instanceof String?(String)v:ManifestParser.str(ManifestParser.map(v),"path"));}}
            catch(Exception e){warn.accept("Steam bibliotecas: "+e.getMessage());}
        }
        List<Game> games=new ArrayList<>();for(Path lib:libs){Path apps=lib.resolve("steamapps");if(!Files.isDirectory(apps))continue;
            try(DirectoryStream<Path> files=Files.newDirectoryStream(apps,"appmanifest_*.acf")){for(Path f:files)try{
                Map<String,Object> m=ManifestParser.map(ManifestParser.vdf(Files.readString(f)).get("AppState"));String id=ManifestParser.str(m,"appid"),name=ManifestParser.str(m,"name"),dir=ManifestParser.str(m,"installdir");
                if(!id.matches("\\d+")||name.isBlank()||dir.isBlank()||id.equals("228980")||name.toLowerCase(Locale.ROOT).startsWith("steamworks common redistributables"))continue;
                Path common=apps.resolve("common").toAbsolutePath().normalize(), folder=common.resolve(dir).normalize();if(!folder.startsWith(common)||folder.equals(common)||!Files.isDirectory(folder))continue;
                String flags=ManifestParser.str(m,"StateFlags");if(!flags.isBlank()&&(Long.parseLong(flags)&4)==0)continue;
                games.add(new Game("steam:"+id,name,"Steam",folder.toString(),"","steam://run/"+id,true,"",0,false));
            }catch(Exception e){warn.accept(f.getFileName()+": "+e.getMessage());}}
        }return games;
    }
    static List<Game> epic(Path manifests,Consumer<String> warn) throws IOException {
        List<Game> games=new ArrayList<>();if(!Files.isDirectory(manifests))return games;
        try(DirectoryStream<Path> files=Files.newDirectoryStream(manifests,"*.item")){for(Path f:files)try{
            Map<String,Object> m=ManifestParser.json(Files.readString(f));String id=ManifestParser.str(m,"AppName"),name=ManifestParser.str(m,"DisplayName"),loc=ManifestParser.str(m,"InstallLocation"),exe=ManifestParser.str(m,"LaunchExecutable");
            if(id.isBlank()||name.isBlank()||loc.isBlank()||Boolean.TRUE.equals(m.get("bIsIncompleteInstall"))||Boolean.TRUE.equals(m.get("bIsDLC")))continue;
            Path folder=Path.of(loc).toAbsolutePath().normalize();if(!Files.isDirectory(folder))continue;
            String main=ManifestParser.str(m,"MainGameAppName");if(!main.isBlank()&&!main.equals(id))continue;
            Path executable=folder.resolve(exe).normalize();if(exe.isBlank()||!executable.startsWith(folder)||!Files.isRegularFile(executable))continue;
            String namespace=ManifestParser.str(m,"CatalogNamespace"),catalog=ManifestParser.str(m,"CatalogItemId");
            String target=namespace.isBlank()||catalog.isBlank()?id:namespace+":"+catalog+":"+id;
            String uri="com.epicgames.launcher://apps/"+java.net.URLEncoder.encode(target,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20")+"?action=launch&silent=true";
            games.add(new Game("epic:"+id,name,"Epic Games",folder.toString(),executable.toString(),uri,true,"",0,false));
        }catch(Exception e){warn.accept(f.getFileName()+": "+e.getMessage());}}return games;
    }
    static List<Game> roblox(Path versions) throws IOException {
        if(!Files.isDirectory(versions))return List.of();
        try(Stream<Path> paths=Files.list(versions)) {Optional<Path> latest=paths.map(p->p.resolve("RobloxPlayerBeta.exe")).filter(Files::isRegularFile).max(Comparator.comparingLong(Detector::modified));
            return latest.map(p->List.of(new Game("roblox:player","Roblox","Local",versions.toAbsolutePath().normalize().toString(),p.toAbsolutePath().normalize().toString(),"https://www.roblox.com/games",true,"",0,false))).orElse(List.of());}
    }
    private static long modified(Path p){try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException e){return 0;}}
    static List<Game> riot(){List<Game> list=new ArrayList<>();for(File drive:File.listRoots()){
        Path base=drive.toPath().resolve("Riot Games"), client=base.resolve("Riot Client/RiotClientServices.exe");
        if(!Files.isRegularFile(client))continue;
        for(String[] game:new String[][]{{"league_of_legends","League of Legends","League of Legends/Game/League of Legends.exe"},{"valorant","VALORANT","VALORANT/live/ShooterGame/Binaries/Win64/VALORANT-Win64-Shipping.exe"}}){Path exe=base.resolve(game[2]);if(Files.isRegularFile(exe))list.add(new Game("riot:"+game[0],game[1],"Riot Games",base.resolve(game[2].split("/")[0]).toString(),exe.toString(),"",true,"",0,false));}
    }return list;}
}
