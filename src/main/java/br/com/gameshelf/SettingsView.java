package br.com.gameshelf;

import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static br.com.gameshelf.ui.Ui.*;

final class SettingsView {
    private SettingsView() {}
    static Node build(MainWindow app){
        VBox page=new VBox(17,label("Configurações","page-title"));page.getStyleClass().add("page");
        VBox look=panel("Aparência");
        var accent=new ComboBox<String>();var colors=List.of("#929cff","#63b7aa","#da98c1");
        accent.getItems().addAll("Lavanda","Menta","Rosa");accent.getSelectionModel().select(Math.max(0,colors.indexOf(app.library.setting("accent",colors.getFirst()))));
        accent.setOnAction(e->{app.prefer("accent",colors.get(accent.getSelectionModel().getSelectedIndex()));app.applyTheme();});
        ComboBox<String> size=new ComboBox<>();size.getItems().addAll("Pequenas","Médias","Grandes");
        var widths=List.of("134","176","222");size.getSelectionModel().select(Math.max(0,widths.indexOf(app.library.setting("coverSize","176"))));
        var preview=new br.com.gameshelf.ui.Poster(1);
        Runnable resizePreview=()->{double w=Integer.parseInt(widths.get(size.getSelectionModel().getSelectedIndex()))*.26;preview.setMinSize(w,w*1.5);preview.setMaxSize(w,w*1.5);};
        resizePreview.run();
        size.setOnAction(e->{app.prefer("coverSize",widths.get(size.getSelectionModel().getSelectedIndex()));resizePreview.run();});
        look.getChildren().addAll(line("Tema claro",toggle(app,"light",false,app::applyTheme)),line("Cor de destaque",accent),
            line("Tamanho das capas",row(10,preview,size)),line("Animações da interface",toggle(app,"animations",true,()->{})),
            line("Menu lateral recolhido",toggle(app,"compact",false,app::sidebar)));
        VBox library=panel("Biblioteca e monitoramento");
        CheckBox tray=toggle(app,"tray",true,()->{});tray.setDisable(!app.tray.available());
        library.getChildren().addAll(line("Buscar instalações ao iniciar",toggle(app,"scanOnStart",true,()->{})),
            line("Fechar a janela para a bandeja",tray),
            line("Capas e banners da Steam",button("Tentar novamente","refresh","quiet",()->{app.art.retry();app.shelfArtworkChanged();app.message("Busca de imagens reativada.");})));
        VBox data=panel("Dados locais");
        ComboBox<Integer> limit=new ComboBox<>();limit.getItems().addAll(64,128,256,512);
        limit.setValue(Integer.valueOf(app.library.setting("cacheMb","256")));
        limit.setOnAction(e->{app.prefer("cacheMb",limit.getValue().toString());app.art.limit(limit.getValue());});
        data.getChildren().addAll(line("Limite do cache de imagens (MB)",limit),
            button("Limpar cache de imagens",null,"quiet",()->app.work(()->{
                app.art.trim(0);javafx.application.Platform.runLater(()->{app.art.retry();app.message("Cache de imagens limpo.");});
            })));
        FlowPane exports=new FlowPane(8,8,button("Backup SQLite","folder","quiet",()->export(app,"db")),
            button("Exportar biblioteca",null,"quiet",()->export(app,"games")),
            button("Exportar histórico",null,"quiet",()->export(app,"sessions")));
        data.getChildren().add(exports);
        VBox catalog=panel("SteamGridDB");
        PasswordField key=new PasswordField();key.setPromptText("Chave pessoal opcional");key.setText(app.library.setting("gridDbKey",""));
        HBox.setHgrow(key,Priority.ALWAYS);
        catalog.getChildren().addAll(label("A chave é salva no banco local e incluída no backup SQLite.","muted"),
            row(8,key,button("Salvar chave",null,"quiet",()->{app.prefer("gridDbKey",key.getText().strip());app.message("Configuração do SteamGridDB salva.");})));
        page.getChildren().addAll(look,library,catalog,data,button("Sair do GameShelf",null,"quiet",app::exit));return scroll(page);
    }
    private static void export(MainWindow app,String type){
        FileChooser fc=new FileChooser();boolean db=type.equals("db");fc.setTitle("Salvar cópia");
        fc.setInitialFileName(db?"gameshelf-backup.db":"gameshelf-"+type+".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(db?"Banco SQLite":"CSV",db?"*.db":"*.csv"));
        File dest=fc.showSaveDialog(app.stage);if(dest==null)return;
        app.work(()->{
            if(db)app.library.backup(dest.toPath());
            else try(var out=Files.newBufferedWriter(dest.toPath(),StandardCharsets.UTF_8)){
                if(type.equals("games")){
                    out.write("id,nome,plataforma,instalado,favorito,segundos");out.newLine();
                    for(Game g:app.library.snapshot())csv(out,g.id(),g.name(),g.platform(),g.installed(),g.favorite(),g.seconds());
                }else{
                    out.write("jogo_id,inicio_utc,fim_utc,segundos");out.newLine();
                    for(Session s:app.library.history(null))csv(out,s.gameId(),java.time.Instant.ofEpochSecond(s.start()),java.time.Instant.ofEpochSecond(s.end()),s.seconds());
                }
            }
            javafx.application.Platform.runLater(()->app.message("Cópia salva em "+dest));
        });
    }
    private static void csv(BufferedWriter out,Object... fields)throws IOException{
        StringJoiner line=new StringJoiner(",");for(Object f:fields)line.add("\""+f.toString().replace("\"","\"\"")+"\"");out.write(line.toString());out.newLine();
    }
    static Node about(){
        VBox page=new VBox(18,label("Sobre","page-title"));page.getStyleClass().add("page");
        VBox card=panel("GameShelf");Label summary=label("Biblioteca de jogos para Windows. Reúna suas instalações e acompanhe o tempo de jogo no computador.","muted");
        summary.setWrapText(true);card.getChildren().addAll(summary,label("JavaFX 21   ·   SQLite   ·   Versão 0.1.0","muted"));page.getChildren().add(card);
        VBox support=panel("Detecção de jogos");Label note=label("Busca automática na Steam, Epic Games e Roblox. O suporte à Riot Games é experimental. As outras plataformas aceitam cadastro manual.","muted");
        note.setWrapText(true);support.getChildren().add(note);page.getChildren().add(support);return scroll(page);
    }
    private static Node scroll(VBox page){var s=new ScrollPane(page);s.setFitToWidth(true);s.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);return s;}
    private static HBox line(String name,Node control){return row(12,label(name,"setting-name"),spring(),control);}
    private static VBox panel(String title){VBox v=new VBox(14,label(title,"section-title"));v.getStyleClass().add("panel");return v;}
    private static CheckBox toggle(MainWindow app,String key,boolean defaultValue,Runnable after){
        CheckBox c=new CheckBox();c.setAccessibleText(key);c.setSelected(app.flag(key,defaultValue));
        c.setOnAction(e->{app.prefer(key,String.valueOf(c.isSelected()));after.run();});return c;
    }
}
