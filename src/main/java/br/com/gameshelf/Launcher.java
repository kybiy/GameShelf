package br.com.gameshelf;

import br.com.gameshelf.service.i18n.LanguagePreference;
import br.com.gameshelf.service.i18n.LocalizationService;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.*;
import java.io.IOException;

public final class Launcher {
    private static final LocalizationService DEFAULT_I18N = new LocalizationService(LanguagePreference.SYSTEM);

    public static void open(Game game) throws IOException {
        open(game, "", "", DEFAULT_I18N);
    }

    public static void open(Game game, String arguments, String workingDirectory) throws IOException {
        open(game, arguments, workingDirectory, DEFAULT_I18N);
    }

    public static void open(Game game, String arguments, String workingDirectory, LocalizationService i18n) throws IOException {
        LocalizationService loc = i18n == null ? DEFAULT_I18N : i18n;
        if (!game.installed()) throw new IOException(loc.t("launcher.err.notInstalled"));
        if (!game.launchUri().isBlank()) {
            URI uri = URI.create(game.launchUri());
            boolean known=game.id().matches("steam:[0-9]+") && game.launchUri().equals("steam://run/"+game.id().substring(6))
                    || game.id().startsWith("epic:") && "com.epicgames.launcher".equals(uri.getScheme()) && "apps".equals(uri.getHost())
                    || game.robloxPlayer() && game.launchUri().equals("https://www.roblox.com/games");
            if (!known)
                throw new IOException(loc.t("launcher.err.protocol"));
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IOException(loc.t("launcher.err.noBrowse"));
            Desktop.getDesktop().browse(uri);
            return;
        }
        if (!game.manual() && game.platform().equals("Riot Games")) {
            Path client = Path.of(game.folder()).getParent().resolve("Riot Client/RiotClientServices.exe");
            String product = game.id().substring("riot:".length());
            new ProcessBuilder(client.toString(), "--launch-product=" + product, "--launch-patchline=live")
                    .directory(client.getParent().toFile()).start();
            return;
        }
        Path exe = Path.of(game.executable());
        if (!Files.isRegularFile(exe)) throw new IOException(loc.t("launcher.err.exeMissing"));
        if (exe.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".lnk")) {
            Desktop.getDesktop().open(exe.toFile());
            return;
        }
        if (!exe.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".exe"))throw new IOException(loc.t("launcher.err.exeMissing"));
        var command = new java.util.ArrayList<String>();
        command.add(exe.toString());
        arguments.lines().filter(line -> !line.isEmpty()).forEach(command::add);
        Path cwd = workingDirectory.isBlank() ? exe.getParent() : Path.of(workingDirectory);
        new ProcessBuilder(command).directory(cwd.toFile()).start();
    }
}
