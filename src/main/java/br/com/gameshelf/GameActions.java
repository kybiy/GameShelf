package br.com.gameshelf;

import br.com.gameshelf.ui.Artwork;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import static br.com.gameshelf.ui.Ui.*;

final class GameActions {
    private final MainWindow app;
    GameActions(MainWindow app){this.app=app;}
    Alert alert(Alert.AlertType type,String title,String body){
        Alert a=new Alert(type,body);a.setTitle("GameShelf");a.setHeaderText(title);style(a);return a;
    }
    private void style(Dialog<?> dialog){
        dialog.initOwner(app.stage);
        var pane=dialog.getDialogPane();pane.getStylesheets().setAll(app.root.getScene().getStylesheets());
        if(app.flag("light",false))pane.getStyleClass().add("light");
        pane.setStyle(app.root.getStyle());
    }
    private static MenuItem item(String title,Runnable action){MenuItem i=new MenuItem(title);i.setOnAction(e->action.run());return i;}
    ContextMenu menu(Game g){
        MenuItem play=item("Jogar",()->launch(g));play.setDisable(!g.installed()||app.tracker.sessions().containsKey(g.id()));
        var menu=new ContextMenu(play,item("Ver detalhes",()->app.details(g)),item(g.favorite()?"Remover dos favoritos":"Adicionar aos favoritos",()->favorite(g)),
            new SeparatorMenuItem(),item("Alterar capa…",()->chooseArt(g,Artwork.Kind.COVER)),item("Alterar banner…",()->chooseArt(g,Artwork.Kind.BANNER)),
            item("Pesquisar no SteamGridDB…",()->searchArt(g)),
            item("Monitorar outro executável…",()->monitor(g)));
        if(!g.monitorOverride().isBlank())menu.getItems().add(item("Restaurar monitoramento automático",()->{app.library.monitor(g.id(),"");app.changed();}));
        if(g.manual())menu.getItems().add(item("Remover da biblioteca…",()->remove(g)));
        return menu;
    }
    void launch(Game g){app.work(()->Launcher.open(g,app.library.setting("args."+g.id(),""),app.library.setting("work."+g.id(),"")));}
    void favorite(Game g){app.library.favorite(g.id());app.changed();}
    private Path file(String title,String description,String... patterns){
        FileChooser fc=new FileChooser();fc.setTitle(title);fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(description,patterns));
        File f=fc.showOpenDialog(app.stage);return f==null?null:f.toPath().toAbsolutePath().normalize();
    }
    private Path picture(String title){return file(title,"Imagens","*.jpg","*.jpeg","*.png");}
    private void searchArt(Game g){
        String key=app.library.setting("gridDbKey","");
        if(key.isBlank()){alert(Alert.AlertType.INFORMATION,"Pesquisar no SteamGridDB","Configure sua chave pessoal em Configurações para acessar o catálogo.").showAndWait();return;}
        TextInputDialog search=new TextInputDialog(g.name());search.setHeaderText("Pesquisar jogo no SteamGridDB");style(search);
        search.showAndWait().filter(query->!query.isBlank()).ifPresent(query->app.work(()->{
            GridDb service=new GridDb(key);var found=service.search(query);
            javafx.application.Platform.runLater(()->{
                if(found.isEmpty()){app.message("Nenhum jogo encontrado no SteamGridDB.");return;}
                ChoiceDialog<GridDb.Match> choice=new ChoiceDialog<>(found.getFirst(),found);style(choice);choice.setHeaderText("Selecione a edição correta de "+g.name());
                choice.showAndWait().ifPresent(match->{
                    var type=new ChoiceDialog<>("Capa",List.of("Capa","Banner"));style(type);type.setHeaderText("Escolha o tipo de imagem");
                    type.showAndWait().ifPresent(value->{
                        boolean cover=value.equals("Capa");
                        app.work(()->{var images=service.images(match.id(),cover);
                            javafx.application.Platform.runLater(()->selectArt(g,cover?Artwork.Kind.COVER:Artwork.Kind.BANNER,images));});
                    });
                });
            });
        }));
    }
    private void selectArt(Game g,Artwork.Kind kind,List<GridDb.Art> images){
        if(images.isEmpty()){app.message("Nenhuma imagem encontrada para essa edição.");return;}
        Dialog<ButtonType> dialog=new Dialog<>();style(dialog);dialog.setTitle("SteamGridDB");dialog.setHeaderText("Escolher imagem para "+g.name());
        ComboBox<GridDb.Art> choices=new ComboBox<>();choices.getItems().setAll(images);choices.getSelectionModel().selectFirst();
        var preview=new StackPane();boolean cover=kind==Artwork.Kind.COVER;
        preview.setPrefSize(cover?180:400,cover?270:160);
        Runnable display=()->preview.getChildren().setAll(new br.com.gameshelf.ui.Poster(g,kind,choices.getValue().url(),app.art));
        choices.setOnAction(e->display.run());display.run();
        dialog.getDialogPane().setContent(new VBox(8,choices,preview));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL,ButtonType.OK);
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){
            app.library.settingValue((cover?"cover.":"banner.")+g.id(),choices.getValue().url());app.shelfArtworkChanged();app.changed();
        }
    }
    private void chooseArt(Game g,Artwork.Kind kind){
        Path p=picture(kind==Artwork.Kind.COVER?"Alterar capa":"Alterar banner");
        if(p==null)return;
        app.library.settingValue((kind==Artwork.Kind.COVER?"cover.":"banner.")+g.id(),p.toString());
        app.shelfArtworkChanged();app.changed();
    }
    private void monitor(Game g){
        Path p=file("Selecionar o processo do jogo","Executável","*.exe");
        if(p!=null){app.library.monitor(g.id(),p.toString());app.changed();}
    }
    private void remove(Game g){
        if(alert(Alert.AlertType.CONFIRMATION,"Remover "+g.name()+"?","O cadastro, as horas e o histórico deste jogo serão excluídos. Os arquivos instalados serão preservados.")
            .showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){app.library.remove(g.id());app.changed();}
    }
    private static TextField field(String value){return new TextField(value);}
    private static void setPath(TextField field,Path p){if(p!=null)field.setText(p.toString());}
    private HBox picker(TextField field,Runnable action){HBox.setHgrow(field,Priority.ALWAYS);return row(6,field,tool("Escolher arquivo","folder",action));}
    void add(Path dropped){
        Path executable=dropped==null?file("Adicionar jogo","Executáveis e atalhos do Windows","*.exe","*.lnk"):dropped;
        if(executable==null)return;
        Path absolute=executable.toAbsolutePath().normalize();
        String path=absolute.toString(),filename=absolute.getFileName().toString();
        String extension=filename.toLowerCase(Locale.ROOT);
        if(!Files.isRegularFile(absolute)||!(extension.endsWith(".exe")||extension.endsWith(".lnk"))){
            alert(Alert.AlertType.WARNING,"Arquivo incompatível","Selecione um executável (.exe) ou atalho (.lnk).").showAndWait();return;
        }
        if(app.library.snapshot().stream().anyMatch(g->g.executable().equalsIgnoreCase(path))){
            alert(Alert.AlertType.INFORMATION,"Jogo já cadastrado","Este arquivo já aparece na sua biblioteca.").showAndWait();return;
        }
        Dialog<ButtonType> dialog=new Dialog<>();dialog.setTitle("Adicionar jogo");dialog.setHeaderText("Adicionar à biblioteca");style(dialog);
        ButtonType add=new ButtonType("Adicionar jogo",ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,add);
        TextField name=field(filename.substring(0,filename.lastIndexOf('.'))),hours=field("0"),monitor=field(""),cover=field(""),banner=field("");
        TextField folder=field(absolute.getParent().toString()),workDir=field(absolute.getParent().toString());
        TextArea args=new TextArea();args.setPromptText("Um argumento por linha");args.setPrefRowCount(2);
        ComboBox<String> platform=new ComboBox<>();platform.getItems().setAll(MainWindow.PLATFORMS);platform.setValue("Local");
        Label error=label("","error");error.setWrapText(true);Label target=label(path,"muted");target.setWrapText(true);
        VBox advanced=new VBox(6,label("Pasta de instalação","muted"),folder,label("Diretório de trabalho","muted"),workDir,
            label("Argumentos de inicialização","muted"),args,label("Executável monitorado","muted"),
            picker(monitor,()->setPath(monitor,file("Selecionar executável","Executável","*.exe"))),
            label("Capa","muted"),picker(cover,()->setPath(cover,picture("Selecionar capa"))),
            label("Banner","muted"),picker(banner,()->setPath(banner,picture("Selecionar banner"))),label("Horas iniciais","muted"),hours);
        var extra=new TitledPane("Opções adicionais",advanced);extra.setExpanded(false);extra.setAnimated(false);
        VBox form=new VBox(8,target,label("Nome do jogo","muted"),name,label("Plataforma","muted"),platform,extra,error);
        form.setPadding(new Insets(6));ScrollPane scroll=new ScrollPane(form);scroll.setFitToWidth(true);scroll.setPrefViewportWidth(430);scroll.setMaxHeight(400);
        dialog.getDialogPane().setContent(scroll);
        String id="local:"+UUID.randomUUID();
        dialog.getDialogPane().lookupButton(add).addEventFilter(ActionEvent.ACTION,e->{
            try{
                if(name.getText().isBlank())throw new IllegalArgumentException("Informe o nome do jogo.");
                double initial=Double.parseDouble(hours.getText().strip().replace(',','.'));
                if(!Double.isFinite(initial)||initial<0||initial>1000000)throw new IllegalArgumentException("Informe uma quantidade válida de horas.");
                for(TextField dir:List.of(folder,workDir))if(!Files.isDirectory(Path.of(dir.getText())))throw new IllegalArgumentException("Diretório inválido.");
                for(TextField f:List.of(monitor,cover,banner))if(!f.getText().isBlank()&&!Files.isRegularFile(Path.of(f.getText())))throw new IllegalArgumentException("Arquivo não encontrado: "+f.getText());
                app.library.add(new Game(id,name.getText().strip(),platform.getValue(),folder.getText(),path,"",true,monitor.getText(),Math.round(initial*3600),false));
                app.library.settingValue("args."+id,args.getText());app.library.settingValue("work."+id,workDir.getText());
                app.library.settingValue("cover."+id,cover.getText());app.library.settingValue("banner."+id,banner.getText());
            }catch(IllegalArgumentException ex){error.setText(ex instanceof NumberFormatException?"Informe as horas iniciais em formato numérico.":ex.getMessage());e.consume();}
        });
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)==add){app.changed();app.details(app.library.get(id));}
    }
}
