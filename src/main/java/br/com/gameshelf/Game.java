package br.com.gameshelf;

/** Identidade estável: horas não dependem do nome ou da pasta atual. */
public record Game(String id, String name, String platform, String folder,
                   String executable, String launchUri, boolean installed,
                   String monitorOverride, long seconds, boolean favorite) {
    public Game withSeconds(long value) { return new Game(id,name,platform,folder,executable,launchUri,installed,monitorOverride,value,favorite); }
    public Game withFavorite(boolean value) { return new Game(id,name,platform,folder,executable,launchUri,installed,monitorOverride,seconds,value); }
    public Game withMonitor(String value) { return new Game(id,name,platform,folder,executable,launchUri,installed,value,seconds,favorite); }
    public Game withInstalled(boolean value) { return new Game(id,name,platform,folder,executable,launchUri,value,monitorOverride,seconds,favorite); }
    public Game preserve(Game old) { return new Game(id,name,platform,folder,executable,launchUri,installed,old.monitorOverride,old.seconds,old.favorite); }
    public boolean robloxPlayer() { return id.equals("roblox:player"); }
    public boolean manual() { return id.startsWith("local:") || (platform.equals("Local") && !robloxPlayer()); }
}
