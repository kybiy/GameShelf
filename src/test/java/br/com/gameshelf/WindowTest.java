package br.com.gameshelf;

import br.com.gameshelf.ui.Artwork;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="gameshelf.uiTest",matches="true")
final class WindowTest {
    @TempDir Path dir;
    private static <T> T fx(Callable<T> job)throws Exception {
        FutureTask<T> task=new FutureTask<>(job);Platform.runLater(task);return task.get(30,TimeUnit.SECONDS);
    }
    @Test void libraryAndDetailsAtTwoWindowSizes() throws Exception {
        Platform.startup(()->{});Platform.setImplicitExit(false);
        Library lib=new Library(dir.resolve("library.properties"));
        MainWindow window=fx(()->new MainWindow(new Stage(),lib,dir));
        boolean live=Boolean.getBoolean("gameshelf.liveArt");
        String[][] fixtures={{"1091500","Cyberpunk 2077"},{"1174180","Red Dead Redemption 2"},{"1145360","Hades"},{"367520","Hollow Knight"},{"620","Portal 2"},{"504230","Celeste"}};
        try {
            fx(()->{
                window.stage.show();var root=window.root;root.applyCss();root.layout();
                assertTrue(root.lookupAll(".section-title").stream().anyMatch(n->((Label)n).getText().contains("começa aqui")));
                for(String[] f:fixtures)lib.add(new Game((live?"steam:":"local:")+f[0],f[1],live?"Steam":"Local","","","",true,"",0,f==fixtures[0]));
                window.navigate("Todos os jogos");window.message("Biblioteca de teste");return null;
            });
            Game game=lib.snapshot().getFirst();
            if(live){
                // Exercise the same asynchronous decoder and cache used by the actual cards.
                CountDownLatch loaded=new CountDownLatch(fixtures.length+1);
                fx(()->{for(Game g:lib.snapshot())window.art.load(g,Artwork.Kind.COVER,"",i->loaded.countDown());
                    window.art.load(game,Artwork.Kind.BANNER,"",i->loaded.countDown());return null;});
                assertTrue(loaded.await(40,TimeUnit.SECONDS),"Steam não entregou todas as imagens a tempo");
            }
            fx(()->{
                for(String size:List.of("134","176","222")){
                    lib.settingValue("coverSize",size);window.navigate("Todos os jogos");snapshot(window.stage.getScene(),"capas-"+size);
                }
                lib.settingValue("coverSize","176");window.navigate("Todos os jogos");
                for(String accent:List.of("#63b7aa","#929cff")){
                    lib.settingValue("accent",accent);window.applyTheme();snapshot(window.stage.getScene(),"cor-"+accent.substring(1));
                }
                snapshot(window.stage.getScene(),"library-wide");
                assertTrue(window.root.lookupAll(".game-tile").size()>=3);
                TextField search=(TextField)window.root.lookup(".search-box .text-field");search.setText(game.name());
                assertTrue(window.root.lookupAll(".muted").stream().anyMatch(n->n instanceof Label l&&l.getText().startsWith("1 jogo ")));
                window.details(game);snapshot(window.stage.getScene(),"details-wide");
                window.navigate("Favoritos");window.stage.setWidth(740);window.stage.setHeight(555);return null;
            });
            fx(()->{
                snapshot(window.stage.getScene(),"library-narrow");
                assertTrue(window.root.getWidth()<800);
                window.details(game);snapshot(window.stage.getScene(),"details-narrow");
                assertTrue(window.root.lookupAll(".section-title").stream().anyMatch(n->((Label)n).getText().contains("Histórico")));
                window.navigate("Estatísticas");window.root.applyCss();window.root.layout();
                window.navigate("Configurações");window.root.applyCss();window.root.layout();
                verifyPlatformNavigation(window);window.stage.setMaximized(true);return null;
            });
            fx(()->{assertTrue(window.stage.isMaximized());verifyPlatformNavigation(window);window.stage.setMaximized(false);return null;});
            fx(()->{assertFalse(window.stage.isMaximized());lib.settingValue("animations","false");verifyPlatformNavigation(window);assertEquals(1.0,window.root.getCenter().getOpacity());return null;});
            lib.save();Library reopened=new Library(dir.resolve("library.properties"));
            assertEquals("false",reopened.setting("animations",""));assertEquals("176",reopened.setting("coverSize",""));assertEquals("#929cff",reopened.setting("accent",""));
        }finally{
            fx(()->{window.stop();window.stage.hide();return null;});Platform.exit();
        }
    }
    private static void verifyPlatformNavigation(MainWindow window) {
        window.root.applyCss();window.root.layout();
        var side=window.root.getLeft();
        ScrollPane scroll=(ScrollPane)side.lookup(".scroll-pane");
        assertFalse(MainWindow.PLATFORMS.contains("Roblox"));
        for(String platform:MainWindow.PLATFORMS){
            String title=platform.equals("Local")?"Jogos locais":platform;
            Button button=side.lookupAll(".nav").stream().map(Button.class::cast)
                .filter(b->title.equals(b.getAccessibleText())).findFirst().orElseThrow();
            double overflow=scroll.getContent().getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight();
            if(overflow>0)scroll.setVvalue(Math.clamp(button.getBoundsInParent().getMinY()/overflow,0,1));
            window.root.layout();double position=scroll.getVvalue();
            button.fire();window.changed();window.root.applyCss();window.root.layout();
            assertEquals(platform,window.page());assertTrue(button.getStyleClass().contains("selected"));
            assertSame(scroll,window.root.getLeft().lookup(".scroll-pane"));assertEquals(position,scroll.getVvalue(),1e-8);
            var viewport=scroll.lookup(".viewport");
            assertTrue(viewport.localToScene(viewport.getLayoutBounds()).intersects(button.localToScene(button.getLayoutBounds())),title);
        }
    }
    private static void snapshot(Scene scene,String name)throws Exception{
        scene.getRoot().applyCss();scene.getRoot().layout();
        var image=scene.getRoot().snapshot(null,null);int width=(int)image.getWidth(),height=(int)image.getHeight();
        var png=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        int[] pixels=new int[width*height];
        image.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
        png.setRGB(0,0,width,height,pixels,0,width);
        Path path=Path.of("target","ui-test",name+".png");Files.createDirectories(path.getParent());ImageIO.write(png,"png",path.toFile());
    }
}
