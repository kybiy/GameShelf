package br.com.gameshelf;

import br.com.gameshelf.ui.*;
import javafx.animation.TranslateTransition;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.io.File;
import java.util.*;
import static br.com.gameshelf.ui.Ui.*;

final class LibraryView extends VBox {
    private final MainWindow app;
    private final Label heading=label("Minha biblioteca","page-title"),count=label("","muted");
    private final TextField search=new TextField();
    private final Button scan,grid,list;
    private final ComboBox<String> platform=choice("Todas as plataformas"),install=choice("Qualquer instalação","Instalados","Não instalados"),
        sort=choice("Nome (A–Z)","Mais jogados","Última sessão","Adicionados recentemente");
    private final ListView<List<Game>> rows=new ListView<>();
    private final StackPane body=new StackPane();
    private String section="",lastEmpty="";
    private List<Game> shown=List.of();
    private Set<String> active=Set.of();
    private boolean listMode;
    private int columns;
    private double cardWidth;
    LibraryView(MainWindow app) {
        this.app=app;
        getStyleClass().add("page");
        listMode=app.flag("list",false);
        scan=tool("Atualizar biblioteca","refresh",app::scan);
        Button add=button("Adicionar jogo","add","primary",()->app.actions.add(null));
        HBox head=row(9,heading,spring(),scan,add);
        search.setPromptText("Buscar jogos…");search.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(search,Priority.ALWAYS);
        HBox input=row(6,icon("search"),search);input.getStyleClass().add("search-box");HBox.setHgrow(input,Priority.ALWAYS);
        grid=tool("Ver em grade","grid",()->mode(false));list=tool("Ver em lista","list",()->mode(true));
        HBox switcher=row(1,grid,list);switcher.getStyleClass().add("switcher");
        HBox searchRow=row(12,input,switcher);
        platform.getItems().addAll(MainWindow.PLATFORMS);
        FlowPane filters=new FlowPane(8,6,platform,install,sort);filters.setAlignment(Pos.CENTER_LEFT);
        platform.setAccessibleText("Plataforma");install.setAccessibleText("Instalação");sort.setAccessibleText("Ordenação");
        search.textProperty().addListener((o,a,b)->refresh());
        for(var f:List.of(platform,install,sort)) f.setOnAction(e->refresh());
        search.setAccessibleText("Buscar jogos");
        rows.getStyleClass().add("game-list");rows.setFocusTraversable(false);rows.setCellFactory(v->new GameRow());
        widthProperty().addListener((o,a,b)->layoutRows());
        body.widthProperty().addListener((o,a,b)->layoutRows());
        VBox.setVgrow(body,Priority.ALWAYS);
        getChildren().addAll(head,searchRow,filters,count,body);
        setOnDragOver(e->{if(e.getDragboard().hasFiles())e.acceptTransferModes(TransferMode.COPY);e.consume();});
        setOnDragDropped(e->{
            List<File> files=e.getDragboard().getFiles();
            e.setDropCompleted(!files.isEmpty());e.consume();
            for(File f:files)app.actions.add(f.toPath());
        });
        updateModeButtons();
    }
    private static ComboBox<String> choice(String... values) {
        ComboBox<String> c=new ComboBox<>();c.getItems().addAll(values);c.getSelectionModel().selectFirst();return c;
    }
    void open(String name) {
        if(!name.equals(section)){
            section=name;platform.getSelectionModel().select(MainWindow.PLATFORMS.contains(name)?name:"Todas as plataformas");
            platform.setDisable(MainWindow.PLATFORMS.contains(name));search.clear();
        }
        heading.setText(switch(name){case "Todos os jogos"->"Minha biblioteca";case "Local"->"Jogos locais";default->name;});
        layoutRows();refresh();
    }
    void scanning(boolean value){scan.setDisable(value);}
    private void mode(boolean listView){
        listMode=listView;app.prefer("list",Boolean.toString(listMode));updateModeButtons();layoutRows();
    }
    private void updateModeButtons(){
        grid.getStyleClass().remove("selected");list.getStyleClass().remove("selected");
        (listMode?list:grid).getStyleClass().add("selected");
    }
    void refresh() {
        if(section.isEmpty())return;
        List<Game> all=app.library.snapshot();
        Map<String,Long> last=new HashMap<>();
        if(section.equals("Recentes")||sort.getSelectionModel().getSelectedIndex()==2)
            app.library.history(null).forEach(s->last.merge(s.gameId(),s.end(),Math::max));
        int order=section.equals("Recentes")?2:sort.getSelectionModel().getSelectedIndex();
        sort.setVisible(!section.equals("Recentes"));sort.setManaged(sort.isVisible());
        Comparator<Game> byName=Comparator.comparing(Game::name,String.CASE_INSENSITIVE_ORDER);
        Comparator<Game> by=switch(order){
            case 1 -> Comparator.comparingLong(Game::seconds).reversed();
            case 2 -> Comparator.<Game>comparingLong(g->last.getOrDefault(g.id(),0L)).reversed();
            case 3 -> Comparator.<Game>comparingLong(g->Long.parseLong(app.library.setting("added."+g.id(),"0"))).reversed();
            default -> byName;
        };
        String query=search.getText().strip().toLowerCase(Locale.ROOT);
        List<Game> result=all.stream().filter(g->g.name().toLowerCase(Locale.ROOT).contains(query))
            .filter(g->platform.getSelectionModel().getSelectedIndex()==0||g.platform().equals(platform.getValue()))
            .filter(g->install.getSelectionModel().getSelectedIndex()==0||g.installed()==(install.getSelectionModel().getSelectedIndex()==1))
            .filter(g->!section.equals("Favoritos")||g.favorite()).filter(g->!section.equals("Recentes")||last.containsKey(g.id()))
            .sorted(by.thenComparing(byName)).toList();
        var now=app.tracker.sessions().keySet();
        count.setText(result.size()+(result.size()==1?" jogo":" jogos")+"   /   "+platform.getValue());
        if(result.isEmpty()){
            String state=all.isEmpty()?"library":query.isEmpty()&&install.getSelectionModel().getSelectedIndex()==0?section:"filter";
            if(!lastEmpty.equals(state)){body.getChildren().setAll(empty(state));lastEmpty=state;}
            shown=List.of();rows.getItems().clear();
        }else{
            boolean same=shown.size()==result.size()&&now.equals(active);
            for(int i=0;same&&i<shown.size();i++){
                Game a=shown.get(i),b=result.get(i);
                same=a.withSeconds(a.seconds()/60).equals(b.withSeconds(b.seconds()/60));
            }
            shown=result;active=Set.copyOf(now);
            if(!lastEmpty.isEmpty()||!body.getChildren().contains(rows)){body.getChildren().setAll(rows);same=false;}
            lastEmpty="";
            if(!same)rebuild();
        }
    }
    void invalidate(){rows.refresh();}
    private void layoutRows() {
        double available=Math.max(180,body.getWidth()-22);
        int target=Integer.parseInt(app.library.setting("coverSize","176"));
        columns=listMode?1:Math.max(1,(int)((available+16)/(target+16)));
        cardWidth=listMode?available:Math.min(target,available);
        rebuild();
    }
    private void rebuild(){
        if(columns<1||shown.isEmpty())return;
        List<List<Game>> chunks=new ArrayList<>();
        for(int i=0;i<shown.size();i+=columns)chunks.add(shown.subList(i,Math.min(i+columns,shown.size())));
        rows.getItems().setAll(chunks);rows.refresh();
    }
    private Node empty(String state){
        boolean library=state.equals("library");
        String title=switch(state){case "library"->"Sua biblioteca começa aqui";case "Favoritos"->"Nenhum favorito ainda";
            case "Recentes"->"Nenhuma sessão registrada";default->"Nenhum jogo encontrado";};
        String desc=switch(state){case "library"->"Adicione um jogo ou atualize a biblioteca para buscar instalações.";
            case "Favoritos"->"Marque o coração de um jogo para encontrá-lo aqui.";
            case "Recentes"->"Seus jogos aparecem aqui após a primeira sessão monitorada.";
            default->"Experimente outra busca ou altere os filtros.";};
        StackPane illustration=new StackPane();
        for(int i=-1;i<=1;i++){
            Poster p=new Poster(i+1);p.setMinSize(66,92);p.setMaxSize(66,92);
            p.setRotate(i*14);p.setTranslateX(i*40);p.setTranslateY(i==0?-8:0);illustration.getChildren().add(p);
        }
        illustration.getChildren().get(1).toFront();illustration.setPrefHeight(114);
        Label sub=label(desc,"muted");sub.setWrapText(true);sub.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        VBox panel=new VBox(10,illustration,label(title,"section-title"),sub);
        panel.setAlignment(Pos.CENTER);panel.getStyleClass().add("empty-panel");
        if(library)panel.getChildren().add(button("Adicionar jogo","add","primary",()->app.actions.add(null)));
        return panel;
    }
    private final class GameRow extends ListCell<List<Game>> {
        @Override protected void updateItem(List<Game> games,boolean empty) {
            super.updateItem(games,empty);setText(null);
            if(empty||games==null){setGraphic(null);return;}
            HBox line=row(16);line.setPadding(new Insets(3,0,12,0));
            for(Game g:games)line.getChildren().add(entry(g));
            setGraphic(line);
        }
    }
    private Node entry(Game g){
        Poster art=new Poster(g,Artwork.Kind.COVER,app.library.setting("cover."+g.id(),""),app.art);
        StackPane frame=new StackPane(art);
        double w=listMode?46:cardWidth,h=listMode?62:w*1.5;
        frame.setMinSize(w,h);frame.setPrefSize(w,h);frame.setMaxSize(w,h);
        Label name=label(g.name(),"game-title");name.setMaxWidth(listMode?Double.MAX_VALUE:w);
        Label meta=label(g.platform()+(g.seconds()>=60?"   ·   "+ActivityView.shortTime(g.seconds()):""),"muted");
        VBox caption=new VBox(3,name,meta);
        boolean running=active.contains(g.id());
        if(g.favorite()&&!listMode){Label heart=label("♥","cover-heart");StackPane.setAlignment(heart,Pos.TOP_RIGHT);StackPane.setMargin(heart,new Insets(8));frame.getChildren().add(heart);}
        if(running&&!listMode){Label badge=label("●  Jogando","running-badge");StackPane.setAlignment(badge,Pos.BOTTOM_LEFT);StackPane.setMargin(badge,new Insets(8));frame.getChildren().add(badge);}
        Button play=button("Jogar","play","cover-play",()->app.actions.launch(g));
        play.setVisible(false);play.setManaged(false);play.setDisable(!g.installed()||running);
        Region tile;
        if(listMode){
            HBox r=row(14,frame,caption,spring(),label(running?"Em execução":g.installed()?"Instalado":"Não instalado",running?"live":"muted"));
            if(g.favorite())r.getChildren().add(label("♥","accent-text"));
            r.setPrefWidth(cardWidth);r.getStyleClass().add("game-row");tile=r;
        } else {
            frame.getChildren().add(play);play.setManaged(true);
            VBox v=new VBox(8,frame,caption);v.setPrefWidth(w);v.setMaxWidth(w);tile=v;
        }
        tile.getStyleClass().add("game-tile");tile.setFocusTraversable(true);tile.setAccessibleText(g.name()+" · "+g.platform());
        tile.setOnKeyPressed(e->{if(e.getCode()==KeyCode.ENTER){app.details(g);e.consume();}});
        tile.setOnMouseClicked(e->{if(e.getButton()==MouseButton.PRIMARY){app.details(g);e.consume();}});
        tile.setOnContextMenuRequested(e->app.actions.menu(g).show(tile,e.getScreenX(),e.getScreenY()));
        TranslateTransition hover=new TranslateTransition(Duration.millis(170),frame);
        tile.hoverProperty().addListener((o,old,on)->{
            play.setVisible(on&&!listMode&&!play.isDisabled());
            if(!listMode&&app.flag("animations",true)){hover.stop();hover.setToY(on?-3:0);hover.play();}
        });
        return tile;
    }
}
