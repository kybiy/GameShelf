package br.com.gameshelf;

import br.com.gameshelf.ui.*;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import static br.com.gameshelf.ui.Ui.*;

final class ActivityView extends ScrollPane {
    private static final Locale PT=Locale.forLanguageTag("pt-BR");
    private static final ZoneId ZONE=ZoneId.systemDefault();
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd MMM yyyy",PT).withZone(ZONE),
        TIME=DateTimeFormatter.ofPattern("HH:mm",PT).withZone(ZONE);
    private final MainWindow app;
    private final String id;
    private final VBox content=new VBox(18),data=new VBox(17);
    private final Label state=label("","muted");
    private Button play;
    private Label liveDuration;
    private long updatedMinute;
    ActivityView(MainWindow app,Game selected) {
        this.app=app;id=selected==null?null:selected.id();
        setFitToWidth(true);setHbarPolicy(ScrollBarPolicy.NEVER);getStyleClass().add("activity-scroll");
        content.getStyleClass().add("page");setContent(content);
        if(selected!=null){
            Button back=button("Biblioteca","back","quiet",app::back);content.getChildren().add(back);
            Poster banner=new Poster(selected,Artwork.Kind.BANNER,app.library.setting("banner."+id,""),app.art);
            banner.setPrefHeight(210);banner.setMinHeight(140);banner.setMaxWidth(Double.MAX_VALUE);
            Label name=label(selected.name(),"page-title");name.setWrapText(true);
            var fav=tool(selected.favorite()?"Remover dos favoritos":"Adicionar aos favoritos","heart",()->app.actions.favorite(selected));
            if(selected.favorite())fav.getStyleClass().add("selected");
            var options=new MenuButton("");options.setGraphic(icon("more"));options.setAccessibleText("Opções do jogo");
            options.getItems().setAll(app.actions.menu(selected).getItems());
            play=button("Jogar","play","primary",()->app.actions.launch(selected));
            content.getChildren().addAll(banner,name,row(8,state,spring(),fav,options,play));
        }else content.getChildren().add(label("Estatísticas","page-title"));
        content.getChildren().add(data);update();
    }
    static String shortTime(long seconds){
        if(seconds<60)return seconds+"s";
        if(seconds<3600)return (seconds/60)+" min";
        return "%dh %02dmin".formatted(seconds/3600,(seconds%3600)/60);
    }
    private Instant start(LocalDate day){return day.atStartOfDay(ZONE).toInstant();}
    void tick(boolean changed){
        if(changed || System.currentTimeMillis()/60_000!=updatedMinute){update();return;}
        if(liveDuration!=null){
            Long seconds=app.tracker.sessions().get(id);
            liveDuration.setText(seconds==null?"—":Playtime.format(seconds));
        }
    }
    void update(){
        updatedMinute=System.currentTimeMillis()/60_000;
        double pos=getVvalue();data.getChildren().clear();
        LocalDate today=LocalDate.now(ZONE);
        List<Session> sessions=app.library.history(id);
        List<Game> games=app.library.snapshot();
        Instant tomorrow=start(today.plusDays(1));boolean general=id==null;
        Activity activity=new Activity(sessions,general);
        FlowPane metrics=new FlowPane(10,10);
        if(general){
            metrics.getChildren().addAll(metric("Horas dos jogos",shortTime(games.stream().mapToLong(Game::seconds).sum())),
                metric("Hoje",shortTime(activity.between(start(today),tomorrow))),
                metric("Nesta semana",shortTime(activity.between(start(today.with(DayOfWeek.MONDAY)),tomorrow))),
                metric("Neste mês",shortTime(activity.between(start(today.withDayOfMonth(1)),tomorrow))),
                metric("Instalados",Long.toString(games.stream().filter(Game::installed).count())),
                metric("Já jogados",Long.toString(games.stream().filter(g->g.seconds()>0).count())),
                metric("Sessões",Integer.toString(sessions.size())),
                metric("Maior sessão",shortTime(sessions.stream().mapToLong(Session::seconds).max().orElse(0))));
        }else{
            Game g=app.library.get(id);if(g==null)return;
            var active=app.tracker.sessions();boolean live=active.containsKey(id);
            state.setText(g.platform()+"   ·   "+(live?"Em execução":g.installed()?"Instalado":"Não instalado"));
            state.getStyleClass().setAll("label",live?"live":"muted");
            play.setText(live?"Jogando agora":"Jogar");play.setDisable(live||!g.installed());
            liveDuration=label(live?Playtime.format(active.get(id)):"—","metric-value");
            metrics.getChildren().addAll(metric("Tempo total",shortTime(g.seconds())),
                metric("Sessão atual",liveDuration),
                metric("Últimos 7 dias",shortTime(activity.between(start(today.minusDays(6)),tomorrow))),
                metric("Últimos 30 dias",shortTime(activity.between(start(today.minusDays(29)),tomorrow))),
                metric("Última sessão",sessions.isEmpty()?"—":DATE.format(Instant.ofEpochSecond(sessions.getFirst().start()))),
                metric("Sessões",Integer.toString(sessions.size())));
        }
        data.getChildren().add(metrics);
        long[] values=new long[7];String[] labels=new String[7];
        for(int i=0;i<7;i++){LocalDate day=today.minusDays(6-i);labels[i]=day.format(DateTimeFormatter.ofPattern("EEE",PT));values[i]=activity.between(start(day),start(day.plusDays(1)));}
        data.getChildren().add(chart("Atividade nos últimos 7 dias",labels,values));
        if(general){
            data.getChildren().add(label("Os períodos de atividade contam uma vez cada intervalo com jogos simultâneos.","muted"));
            long[] months=new long[6];String[] names=new String[6];
            for(int i=0;i<6;i++){
                LocalDate m=today.withDayOfMonth(1).minusMonths(5-i);months[i]=activity.between(start(m),start(m.plusMonths(1)));
                names[i]=m.format(DateTimeFormatter.ofPattern("MMM",PT));
            }
            data.getChildren().add(chart("Atividade mensal",names,months));
            data.getChildren().add(calendar(today,activity));
            var ranking=panel("Jogos mais jogados");
            games.stream().filter(g->g.seconds()>0).sorted(Comparator.comparingLong(Game::seconds).reversed()).limit(10)
                .forEach(g->ranking.getChildren().add(row(8,label(g.name(),"game-title"),spring(),label(shortTime(g.seconds()),"accent-text"))));
            if(ranking.getChildren().size()==1)ranking.getChildren().add(label("Nenhum tempo de jogo registrado.","muted"));
            var platforms=panel("Horas por plataforma");Map<String,Long> totals=new TreeMap<>();
            games.forEach(g->{if(g.seconds()>0)totals.merge(g.platform(),g.seconds(),Long::sum);});
            totals.forEach((p,n)->platforms.getChildren().add(row(8,label(p,"game-title"),spring(),label(shortTime(n),"muted"))));
            if(totals.isEmpty())platforms.getChildren().add(label("Nenhum tempo de jogo registrado.","muted"));
            data.getChildren().addAll(ranking,platforms);
        }else{
            VBox history=panel("Histórico de sessões");
            if(sessions.isEmpty())history.getChildren().add(label("As sessões aparecerão aqui quando você jogar com o GameShelf aberto.","muted"));
            else{
                var historyRows=new ListView<Session>();historyRows.getItems().setAll(sessions);historyRows.setFixedCellSize(36);
                historyRows.setPrefHeight(Math.min(7,sessions.size())*36+4);historyRows.setCellFactory(v->new ListCell<>(){
                    @Override protected void updateItem(Session s,boolean empty){super.updateItem(s,empty);
                        if(empty||s==null){setText(null);return;}
                        Instant a=Instant.ofEpochSecond(s.start()),b=Instant.ofEpochSecond(s.end());
                        boolean current=s==sessions.getFirst()&&app.tracker.sessions().containsKey(id);
                        String end=current?"agora":(LocalDate.ofInstant(a,ZONE).equals(LocalDate.ofInstant(b,ZONE))?"":DATE.format(b)+" ")+TIME.format(b);
                        setText(DATE.format(a)+"     "+TIME.format(a)+" – "+end+"     ·     "+shortTime(s.seconds()));
                    }
                });history.getChildren().add(historyRows);
            }
            data.getChildren().add(history);
        }
        setVvalue(pos);
    }
    private VBox calendar(LocalDate today,Activity activity) {
        VBox box=panel("Calendário de atividade");GridPane grid=new GridPane();grid.setHgap(4);grid.setVgap(4);
        LocalDate first=today.with(DayOfWeek.MONDAY).minusWeeks(15);
        long seconds=0;int days=0;
        for(LocalDate d=first;!d.isAfter(today);d=d.plusDays(1)){
            long n=activity.between(start(d),start(d.plusDays(1)));seconds+=n;
            Region square=new Region();square.setPrefSize(13,13);square.getStyleClass().add(n==0?"calendar-off":"calendar-on");
            if(n>0)square.setOpacity(Math.min(1,.45+n/14400.));
            Tooltip.install(square,new Tooltip(DATE.format(start(d))+" · "+Playtime.format(n)));grid.add(square,days/7,days%7);days++;
        }
        box.getChildren().addAll(grid,label("Média diária no período · "+shortTime(seconds/days),"muted"));return box;
    }
    private static VBox panel(String title){VBox box=new VBox(12,label(title,"section-title"));box.getStyleClass().add("panel");return box;}
    private static Node metric(String name,String value){return metric(name,label(value,"metric-value"));}
    private static Node metric(String name,Label value){VBox box=new VBox(6,label(name,"muted"),value);box.getStyleClass().add("metric");return box;}
    private static Node chart(String title,String[] labels,long[] values){
        VBox box=panel(title);HBox bars=new HBox(8);bars.setAlignment(Pos.BOTTOM_CENTER);
        long max=Arrays.stream(values).max().orElse(0);
        for(int i=0;i<values.length;i++){
            Region bar=new Region();bar.getStyleClass().add(i==values.length-1?"bar-last":"bar");bar.setMaxWidth(44);
            bar.setPrefHeight(values[i]==0?2:Math.max(3,94.0*values[i]/max));
            VBox col=new VBox(6,bar,label(labels[i],"muted"));col.setPrefWidth(1);col.setPrefHeight(119);col.setAlignment(Pos.BOTTOM_CENTER);
            HBox.setHgrow(col,Priority.ALWAYS);Tooltip.install(col,new Tooltip(labels[i]+" · "+Playtime.format(values[i])));
            bars.getChildren().add(col);
        }
        box.getChildren().add(bars);return box;
    }
}
