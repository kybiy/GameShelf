package br.com.gameshelf;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.*;
import java.io.IOException;

public final class Launcher {
    public static void open(Game game) throws IOException {
        open(game,"","");
    }
    public static void open(Game game,String arguments,String workingDirectory) throws IOException {
        if(!game.installed())throw new IOException("O jogo não está instalado. Atualize a biblioteca.");
        if(!game.launchUri().isBlank()){
            URI uri=URI.create(game.launchUri());if(!java.util.Set.of("steam","com.epicgames.launcher","https").contains(uri.getScheme()))throw new IOException("Protocolo não permitido");
            if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))throw new IOException("O sistema não oferece abertura de links");
            Desktop.getDesktop().browse(uri);return;
        }
        if(!game.manual()&&game.platform().equals("Riot Games")){
            Path client=Path.of(game.folder()).getParent().resolve("Riot Client/RiotClientServices.exe");String product=game.id().substring("riot:".length());
            new ProcessBuilder(client.toString(),"--launch-product="+product,"--launch-patchline=live").directory(client.getParent().toFile()).start();return;
        }
        Path exe=Path.of(game.executable());if(!Files.isRegularFile(exe))throw new IOException("Executável não encontrado");
        if(exe.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".lnk")){Desktop.getDesktop().open(exe.toFile());return;}
        var command=new java.util.ArrayList<String>();command.add(exe.toString());
        arguments.lines().filter(line->!line.isEmpty()).forEach(command::add);
        Path cwd=workingDirectory.isBlank()?exe.getParent():Path.of(workingDirectory);
        new ProcessBuilder(command).directory(cwd.toFile()).start();
    }
}
