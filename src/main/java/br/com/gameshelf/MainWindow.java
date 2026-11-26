package br.com.gameshelf;

import br.com.gameshelf.ui.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.beans.property.*;
import javafx.beans.binding.Bindings;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static br.com.gameshelf.ui.Ui.*;

public final class MainWindow {
    static final List<String> PLATFORMS=List.of("Steam","Epic Games","Riot Games","Xbox","GOG","Ubisoft Connect","EA App","Battle.net","Rockstar Games","Local","Outros");
    final Stage stage;
    final Library library;
    final Playtime tracker;
    final Artwork art;
    final GameActions actions;
    final BorderPane root=new BorderPane();
    final DesktopTray tray=new DesktopTray();
    private final ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("gameshelf-monitor").factory());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("gameshelf-library").factory());
    private final Label status=label("Preparando biblioteca…","muted"), monitorLabel=label("Monitor ativo","monitor-label");
    private final LibraryView shelf;
    private final BooleanProperty compactMenu=new SimpleBooleanProperty();
    private final Map<String,Button> navigation=new HashMap<>();
    private ActivityView activity;
    private String page="Todos os jogos",gameId;
    private Set<String> playing=Set.of();
    private boolean scanning,closing;
    private String scanStatus="Biblioteca local";
    MainWindow(Stage stage,Library library,Path data) {
        this.stage=stage;this.library=library;tracker=new Playtime(library);art=new Artwork(data);
        art.limit(Integer.parseInt(library.setting("cacheMb","256")));
        actions=new GameActions(this);shelf=new LibraryView(this);
        Scene scene=new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/gameshelf.css")).toExternalForm());
        stage.setScene(scene);stage.setTitle("GameShelf");
        var icon=DesktopTray.logo();var fxIcon=new WritableImage(48,48);
        for(int y=0;y<48;y++)for(int x=0;x<48;x++)fxIcon.getPixelWriter().setArgb(x,y,icon.getRGB(x,y));
        stage.getIcons().add(fxIcon);
        var bounds=Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(1280,bounds.getWidth()));stage.setHeight(Math.min(820,bounds.getHeight()));
        stage.setMinWidth(Math.min(720,bounds.getWidth()));stage.setMinHeight(Math.min(510,bounds.getHeight()));
        stage.widthProperty().addListener((o,old,width)->{
            if((old.doubleValue()<940)!=(width.doubleValue()<940)&&library.setting("compact","auto").equals("auto"))sidebar();
        });
        stage.focusedProperty().addListener((o,was,focused)->{
            if(focused){if(root.getCenter()==shelf)shelf.refresh();else if(activity!=null)activity.update();}
        });
        status.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(status,Priority.ALWAYS);
        HBox footer=row(10,status,monitorLabel);footer.getStyleClass().add("footer");root.setBottom(footer);
        stage.setOnCloseRequest(e->{e.consume();if(tray.available()&&flag("tray",true))stage.hide();else exit();});
        stage.setOnShown(e->Platform.runLater(this::themeCaption));applyTheme();sidebar();navigate(page);
    }
    void show() {
        tray.install(()->{stage.show();stage.setIconified(false);stage.toFront();},this::exit);
        Platform.setImplicitExit(false);stage.show();stage.centerOnScreen();
        monitor.scheduleWithFixedDelay(()->{
            try {tracker.poll();Platform.runLater(()->tick(true));}
            catch(Exception e){Platform.runLater(()->tick(false));}
        },0,2,TimeUnit.SECONDS);
        monitor.scheduleWithFixedDelay(()->{try{library.save();}catch(Exception e){report(e);}},5,5,TimeUnit.SECONDS);
        if(flag("scanOnStart",true))scan();else status.setText(scanStatus);
    }
    boolean flag(String key,boolean fallback){return Boolean.parseBoolean(library.setting(key,Boolean.toString(fallback)));}
    void prefer(String key,String value) {library.settingValue(key,value);save();}
    void applyTheme(){
        root.getStyleClass().remove("light");
        if(flag("light",false))root.getStyleClass().add("light");
        themeCaption();
        root.setStyle("-shelf-accent: "+library.setting("accent","#929cff")+";");
    }
    private void themeCaption(){if(stage.isShowing())WindowsCaption.apply(stage.getTitle(),flag("light",false),library.setting("accent","#929cff"));}
    String page(){return page;}
    void navigate(String name) {
        navigation.get(page).getStyleClass().remove("selected");
        page=name;gameId=null;activity=null;
        navigation.get(page).getStyleClass().add("selected");
        switch(name) {
            case "Configurações" -> root.setCenter(SettingsView.build(this));
            case "Sobre" -> root.setCenter(SettingsView.about());
            case "Estatísticas" -> {activity=new ActivityView(this,null);root.setCenter(activity);}
            default -> {root.setCenter(shelf);shelf.open(name);}
        }
    }
    void details(Game g) {
        showDetails(g);enter();
    }
    private void showDetails(Game g){
        gameId=g.id();activity=new ActivityView(this,g);root.setCenter(activity);
    }
    private ParallelTransition entry;
    private void enter(){
        if(entry!=null){entry.stop();Node old=entry.getNode();old.setOpacity(1);old.setScaleX(1);old.setScaleY(1);}
        if(!flag("animations",true))return;
        Node target=root.getCenter();Duration duration=Duration.millis(180);
        FadeTransition fade=new FadeTransition(duration);fade.setFromValue(.65);fade.setToValue(1);
        ScaleTransition zoom=new ScaleTransition(duration);zoom.setFromX(.985);zoom.setFromY(.985);zoom.setToX(1);zoom.setToY(1);
        entry=new ParallelTransition(target,fade,zoom);entry.setInterpolator(Interpolator.EASE_OUT);entry.play();
    }
    void back(){navigate(page);enter();}
    void sidebar() {
        compactMenu.set(flag("compact",stage.getWidth()<940));
        if(root.getLeft()!=null)return;
        VBox links=new VBox(3);
        var logo=icon("library");StackPane mark=new StackPane(logo);mark.getStyleClass().add("brand-mark");
        HBox brand=row(11,mark);
        Label wordmark=label("GameShelf","brand");expandedOnly(wordmark);brand.getChildren().add(wordmark);
        brand.getStyleClass().add("brand-row");
        VBox top=new VBox(brand);
        group(links,"BIBLIOTECA");
        nav(links,"Todos os jogos","library");nav(links,"Favoritos","heart");
        nav(links,"Recentes","clock");nav(links,"Estatísticas","chart");
        group(links,"PLATAFORMAS");
        for(String p:PLATFORMS)nav(links,p,p.equals("Local")?"folder":"platform");
        ScrollPane scrolling=new ScrollPane(links);scrolling.setFitToWidth(true);scrolling.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrolling,Priority.ALWAYS);
        VBox bottom=new VBox(3);bottom.getStyleClass().add("side-bottom");
        nav(bottom,"Configurações","settings");nav(bottom,"Sobre","info");
        Button collapse=button("Recolher menu","menu","nav",()->{prefer("compact",Boolean.toString(!compactMenu.get()));sidebar();});
        collapse.textProperty().bind(Bindings.when(compactMenu).then("").otherwise("Recolher menu"));
        Tooltip hint=new Tooltip();hint.textProperty().bind(Bindings.when(compactMenu).then("Expandir menu").otherwise("Recolher menu"));
        collapse.setTooltip(hint);bottom.getChildren().add(collapse);
        VBox side=new VBox(top,scrolling,bottom);side.getStyleClass().add("sidebar");side.prefWidthProperty().bind(Bindings.when(compactMenu).then(70).otherwise(205));side.setMinWidth(Region.USE_PREF_SIZE);
        root.setLeft(side);
    }
    private void expandedOnly(Node node) {
        node.visibleProperty().bind(compactMenu.not());node.managedProperty().bind(node.visibleProperty());
    }
    private void group(VBox box,String text) {
        Separator separator=new Separator();
        separator.visibleProperty().bind(compactMenu);separator.managedProperty().bind(compactMenu);
        VBox.setMargin(separator,new Insets(12,4,7,4));
        Label title=label(text,"section-label");expandedOnly(title);VBox.setMargin(title,new Insets(17,0,5,10));
        box.getChildren().addAll(separator,title);
    }
    private void nav(VBox box,String name,String glyph){
        String title=name.equals("Local")?"Jogos locais":name;
        Button b=button(title,glyph,"nav",()->{navigate(name);enter();});
        ScaleTransition press=new ScaleTransition(Duration.millis(85),b);
        b.armedProperty().addListener((o,was,armed)->{
            press.stop();double scale=armed?.98:1;
            if(flag("animations",true)){press.setToX(scale);press.setToY(scale);press.play();}
            else {b.setScaleX(1);b.setScaleY(1);}
        });
        if(glyph.equals("platform")){
            String code=switch(name){case "Epic Games" -> "EP"; case "Riot Games" -> "RI";case "Battle.net" -> "B";case "Rockstar Games" -> "R★";case "Ubisoft Connect" -> "U";case "Outros" -> "···";default -> name.substring(0,1);};
            Label monogram=label(code,"platform-mark");monogram.getStyleClass().add("store-"+PLATFORMS.indexOf(name));b.setGraphic(monogram);
        }
        navigation.put(name,b);
        b.textProperty().bind(Bindings.when(compactMenu).then("").otherwise(title));
        b.setMaxWidth(Double.MAX_VALUE);b.setAccessibleText(title);b.tooltipProperty().bind(Bindings.when(compactMenu).then(new Tooltip(title)).otherwise((Tooltip)null));
        box.getChildren().add(b);
    }
    void changed(){
        save();
        if(gameId!=null){Game g=library.get(gameId);if(g!=null)showDetails(g);else back();}
        else if(root.getCenter()==shelf)shelf.refresh();
    }
    private void tick(boolean ok){
        if(closing)return;
        monitorLabel.setText(ok?"●   Monitor ativo":"Monitor indisponível");
        var active=tracker.sessions().keySet();
        if(stage.isShowing()&&!stage.isIconified()){
            if(activity!=null && (!active.isEmpty()||!playing.isEmpty()))activity.tick(!playing.equals(active));
            if(root.getCenter()==shelf && (!playing.equals(active)||!active.isEmpty()))shelf.refresh();
            playing=Set.copyOf(active);
        }
    }
    void scan(){
        if(scanning||closing)return;
        scanning=true;status.setText("Procurando jogos instalados…");shelf.scanning(true);
        work(()->{
            try {
                Detector.Result result=Detector.scan();library.merge(result.games(),result.warnings().isEmpty());library.save();
                scanStatus=result.warnings().isEmpty()?"Biblioteca atualizada · "+result.games().size()+" detectado(s)":String.join("  ·  ",result.warnings());
            }catch(Exception e){scanStatus="Falha na atualização · "+e.getMessage();}
            finally{Platform.runLater(()->{scanning=false;shelf.scanning(false);status.setText(scanStatus);status.setTooltip(new Tooltip(scanStatus));shelf.refresh();if(activity!=null)activity.update();});}
        });
    }
    interface Work {void run() throws Exception;}
    void work(Work job){if(!closing)worker.execute(()->{try{job.run();}catch(Exception e){report(e);}});}
    void save(){work(library::save);}
    void shelfArtworkChanged(){shelf.invalidate();}
    void report(Exception e){Platform.runLater(()->{status.setText("Falha · "+e.getMessage());status.setTooltip(new Tooltip(status.getText()));});}
    void message(String text){status.setText(text);}
    void exit(){
        Alert a=actions.alert(Alert.AlertType.CONFIRMATION,"Encerrar o GameShelf?","O monitor de jogos será encerrado.");
        if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK)Platform.exit();
    }
    void stop() throws Exception {
        closing=true;tray.close();art.close();monitor.shutdownNow();worker.shutdown();
        worker.awaitTermination(6,TimeUnit.SECONDS);monitor.awaitTermination(3,TimeUnit.SECONDS);library.save();
    }
}
