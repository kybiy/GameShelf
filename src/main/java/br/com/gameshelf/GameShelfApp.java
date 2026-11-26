package br.com.gameshelf;

import br.com.gameshelf.service.i18n.LanguagePreference;
import br.com.gameshelf.service.i18n.LocalizationService;
import javafx.application.*;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import java.nio.channels.*;
import java.nio.file.*;

public final class GameShelfApp extends Application {
    private FileChannel channel;
    private FileLock lock;
    private Thread saveHook;
    private Library library;
    private MainWindow window;
    private Path directory;
    private String startupError;
    private boolean notified;
    private LocalizationService startupI18n;

    @Override
    public void init() {
        startupI18n = new LocalizationService(LanguagePreference.SYSTEM);
        try {
            String base = System.getenv("LOCALAPPDATA");
            directory = base == null ? Path.of(System.getProperty("user.home"), ".gameshelf") : Path.of(base, "GameShelf");
            Files.createDirectories(directory);
            channel = FileChannel.open(directory.resolve("instance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = channel.tryLock();
            if (lock == null) {
                try{Files.createFile(directory.resolve("show-window.request"));}catch(FileAlreadyExistsException pending){}
                notified=true;
            } else {
                library = new Library(directory.resolve("library.properties"));
                saveHook=new Thread(()->{try{library.save();}catch(java.io.IOException ignored){/* periodic checkpoints remain available */}},"gameshelf-final-checkpoint");
                Runtime.getRuntime().addShutdownHook(saveHook);
                LanguagePreference pref = LanguagePreference.fromCode(library.setting("language", "system"));
                startupI18n.setPreference(pref);
            }
        } catch (Exception e) {
            startupError = startupI18n.t("startup.openError", e.getMessage());
        }
    }

    @Override
    public void start(Stage stage) {
        if(notified){Platform.exit();return;}
        if (startupError != null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, startupError);
            alert.setHeaderText(startupI18n.t("app.name"));
            alert.showAndWait();
            Platform.exit();
            return;
        }
        window = new MainWindow(stage, library, directory);
        window.show();
    }

    @Override
    public void stop() throws Exception {
        try {
            if (window != null) window.stop();
        } finally {
            if(saveHook!=null)try{Runtime.getRuntime().removeShutdownHook(saveHook);}catch(IllegalStateException shuttingDown){}
            if (lock != null) lock.release();
            if (channel != null) channel.close();
        }
    }
}
