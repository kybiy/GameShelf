package br.com.gameshelf;

import javafx.application.*;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import java.nio.channels.*;
import java.nio.file.*;

public final class GameShelfApp extends Application {
    private FileChannel channel;
    private FileLock lock;
    private Library library;
    private MainWindow window;
    private Path directory;
    private String startupError;

    @Override public void init() {
        try {
            String base=System.getenv("LOCALAPPDATA");
            directory=base==null?Path.of(System.getProperty("user.home"),".gameshelf"):Path.of(base,"GameShelf");
            Files.createDirectories(directory);
            channel=FileChannel.open(directory.resolve("instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            lock=channel.tryLock();
            if(lock==null)startupError="O GameShelf já está em execução. Abra-o pela bandeja.";
            else library=new Library(directory.resolve("library.properties"));
        } catch(Exception e){startupError="Falha ao abrir a biblioteca. "+e.getMessage();}
    }
    @Override public void start(Stage stage) {
        if(startupError!=null){
            Alert alert=new Alert(Alert.AlertType.WARNING,startupError);
            alert.setHeaderText("GameShelf");alert.showAndWait();Platform.exit();return;
        }
        window=new MainWindow(stage,library,directory);
        window.show();
    }
    @Override public void stop() throws Exception {
        try {if(window!=null)window.stop();}
        finally {if(lock!=null)lock.release();if(channel!=null)channel.close();}
    }
}
