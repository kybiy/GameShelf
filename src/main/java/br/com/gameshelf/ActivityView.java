package br.com.gameshelf;

import br.com.gameshelf.ui.*;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.util.*;
import static br.com.gameshelf.ui.Ui.*;

final class ActivityView extends ScrollPane {
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private final MainWindow app;
    private final String id;
    private final VBox content = new VBox(18), data = new VBox(17);
    private final Label state = label("", "muted");
    private Button play;
    private Label liveDuration;
    private long updatedMinute;

    ActivityView(MainWindow app, Game selected) {
        this.app = app;
        this.id = selected == null ? null : selected.id();
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("activity-scroll");
        content.getStyleClass().add("page");
        setContent(content);

        if (selected != null) {
            Button back = button(app.i18n.t("activity.back"), "back", "quiet", app::back);
            content.getChildren().add(back);
            Poster banner = new Poster(selected, Artwork.Kind.BANNER, app.library.setting("banner." + id, ""), app.art);
            banner.setPrefHeight(210);
            banner.setMinHeight(140);
            banner.setMaxWidth(Double.MAX_VALUE);
            Label name = label(selected.name(), "page-title");
            name.setWrapText(true);
            var fav = tool(app.i18n.t(selected.favorite() ? "activity.fav.remove" : "activity.fav.add"), "heart", () -> app.actions.favorite(selected));
            if (selected.favorite()) fav.getStyleClass().add("selected");
            var options = new MenuButton("");
            options.setGraphic(icon("more"));
            options.setAccessibleText(app.i18n.t("activity.options.accessible"));
            options.getItems().setAll(app.actions.menu(selected).getItems());
            play = button(app.i18n.t("activity.play"), "play", "primary", () -> app.actions.launch(selected));
            content.getChildren().addAll(banner, name, row(8, state, spring(), fav, options, play));
        } else {
            content.getChildren().add(label(app.i18n.t("activity.title"), "page-title"));
        }
        content.getChildren().add(data);
        update();
    }

    private Instant start(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }

    void tick(boolean changed) {
        if (changed || System.currentTimeMillis() / 60_000 != updatedMinute) {
            update();
            return;
        }
        if (liveDuration != null) {
            Long seconds = app.tracker.sessions().get(id);
            liveDuration.setText(seconds == null ? "—" : app.i18n.formatDurationClock(seconds));
        }
    }

    void update() {
        updatedMinute = System.currentTimeMillis() / 60_000;
        double pos = getVvalue();
        data.getChildren().clear();
        LocalDate today = LocalDate.now(ZONE);
        List<Session> sessions = app.library.history(id);
        List<Game> games = app.library.snapshot();
        Instant tomorrow = start(today.plusDays(1));
        boolean general = id == null;
        Activity activity = new Activity(sessions, general);
        FlowPane metrics = new FlowPane(10, 10);

        if (general) {
            metrics.getChildren().addAll(
                    metric(app.i18n.t("activity.metric.gamesHours"), app.i18n.formatDurationShort(games.stream().mapToLong(Game::seconds).sum())),
                    metric(app.i18n.t("activity.metric.today"), app.i18n.formatDurationShort(activity.between(start(today), tomorrow))),
                    metric(app.i18n.t("activity.metric.thisWeek"), app.i18n.formatDurationShort(activity.between(start(today.with(DayOfWeek.MONDAY)), tomorrow))),
                    metric(app.i18n.t("activity.metric.thisMonth"), app.i18n.formatDurationShort(activity.between(start(today.withDayOfMonth(1)), tomorrow))),
                    metric(app.i18n.t("activity.metric.installed"), app.i18n.formatInt(games.stream().filter(Game::installed).count())),
                    metric(app.i18n.t("activity.metric.everPlayed"), app.i18n.formatInt(games.stream().filter(g -> g.seconds() > 0).count())),
                    metric(app.i18n.t("activity.metric.sessions"), app.i18n.formatInt(sessions.size())),
                    metric(app.i18n.t("activity.metric.longestSession"), app.i18n.formatDurationShort(sessions.stream().mapToLong(Session::seconds).max().orElse(0)))
            );
        } else {
            Game g = app.library.get(id);
            if (g == null) return;
            var active = app.tracker.sessions();
            boolean live = active.containsKey(id);
            String stateText = app.i18n.t(live ? "library.tile.running" : g.installed() ? "library.tile.installed" : "library.tile.notInstalled");
            state.setText(app.i18n.platformName(g.platform()) + "   ·   " + stateText);
            state.getStyleClass().setAll("label", live ? "live" : "muted");
            play.setText(app.i18n.t(live ? "activity.playingNow" : "activity.play"));
            play.setDisable(live || !g.installed());
            liveDuration = label(live ? app.i18n.formatDurationClock(active.get(id)) : "—", "metric-value");
            metrics.getChildren().addAll(
                    metric(app.i18n.t("activity.metric.totalTime"), app.i18n.formatDurationShort(g.seconds())),
                    metric(app.i18n.t("activity.metric.currentSession"), liveDuration),
                    metric(app.i18n.t("activity.metric.last7"), app.i18n.formatDurationShort(activity.between(start(today.minusDays(6)), tomorrow))),
                    metric(app.i18n.t("activity.metric.last30"), app.i18n.formatDurationShort(activity.between(start(today.minusDays(29)), tomorrow))),
                    metric(app.i18n.t("activity.metric.lastSession"), sessions.isEmpty() ? "—" : app.i18n.formatDate(Instant.ofEpochSecond(sessions.getFirst().start()))),
                    metric(app.i18n.t("activity.metric.sessions"), app.i18n.formatInt(sessions.size()))
            );
        }
        data.getChildren().add(metrics);

        long[] values = new long[7];
        String[] labels = new String[7];
        for (int i = 0; i < 7; i++) {
            LocalDate day = today.minusDays(6 - i);
            labels[i] = app.i18n.formatWeekdayShort(day);
            values[i] = activity.between(start(day), start(day.plusDays(1)));
        }
        data.getChildren().add(chart(app.i18n.t("activity.chart.last7"), labels, values));

        if (general) {
            data.getChildren().add(label(app.i18n.t("activity.chart.overlapNote"), "muted"));
            long[] months = new long[6];
            String[] names = new String[6];
            for (int i = 0; i < 6; i++) {
                LocalDate m = today.withDayOfMonth(1).minusMonths(5 - i);
                months[i] = activity.between(start(m), start(m.plusMonths(1)));
                names[i] = app.i18n.formatMonthShort(m);
            }
            data.getChildren().add(chart(app.i18n.t("activity.chart.monthly"), names, months));
            data.getChildren().add(calendar(today, activity));

            var ranking = panel(app.i18n.t("activity.ranking.title"));
            games.stream()
                    .filter(g -> g.seconds() > 0)
                    .sorted(Comparator.comparingLong(Game::seconds).reversed())
                    .limit(10)
                    .forEach(g -> ranking.getChildren().add(
                            row(8, label(g.name(), "game-title"), spring(), label(app.i18n.formatDurationShort(g.seconds()), "accent-text"))));
            if (ranking.getChildren().size() == 1) ranking.getChildren().add(label(app.i18n.t("activity.noPlaytime"), "muted"));

            var platforms = panel(app.i18n.t("activity.platforms.title"));
            Map<String, Long> totals = new TreeMap<>();
            games.forEach(g -> {
                if (g.seconds() > 0) totals.merge(app.i18n.platformName(g.platform()), g.seconds(), Long::sum);
            });
            totals.forEach((p, n) -> platforms.getChildren().add(row(8, label(p, "game-title"), spring(), label(app.i18n.formatDurationShort(n), "muted"))));
            if (totals.isEmpty()) platforms.getChildren().add(label(app.i18n.t("activity.noPlaytime"), "muted"));
            data.getChildren().addAll(ranking, platforms);
        } else {
            VBox history = panel(app.i18n.t("activity.history.title"));
            if (sessions.isEmpty()) {
                history.getChildren().add(label(app.i18n.t("activity.history.empty"), "muted"));
            } else {
                var historyRows = new ListView<Session>();
                historyRows.getItems().setAll(sessions);
                historyRows.setFixedCellSize(36);
                historyRows.setPrefHeight(Math.min(7, sessions.size()) * 36 + 4);
                historyRows.setCellFactory(v -> new ListCell<>() {
                    @Override
                    protected void updateItem(Session s, boolean empty) {
                        super.updateItem(s, empty);
                        if (empty || s == null) {
                            setText(null);
                            return;
                        }
                        Instant a = Instant.ofEpochSecond(s.start()), b = Instant.ofEpochSecond(s.end());
                        boolean current = s == sessions.getFirst() && app.tracker.sessions().containsKey(id);
                        String end = current ? app.i18n.t("common.now")
                                : (LocalDate.ofInstant(a, ZONE).equals(LocalDate.ofInstant(b, ZONE)) ? "" : app.i18n.formatDate(b) + " ")
                                        + app.i18n.formatTime(b);
                        setText(app.i18n.formatDate(a) + "     " + app.i18n.formatTime(a) + " – " + end
                                + "     ·     " + app.i18n.formatDurationShort(s.seconds()));
                    }
                });
                history.getChildren().add(historyRows);
            }
            data.getChildren().add(history);
        }
        setVvalue(pos);
    }

    private VBox calendar(LocalDate today, Activity activity) {
        VBox box = panel(app.i18n.t("activity.calendar.title"));
        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        LocalDate first = today.with(DayOfWeek.MONDAY).minusWeeks(15);
        long seconds = 0;
        int days = 0;
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            long n = activity.between(start(d), start(d.plusDays(1)));
            seconds += n;
            Region square = new Region();
            square.setPrefSize(13, 13);
            square.getStyleClass().add(n == 0 ? "calendar-off" : "calendar-on");
            if (n > 0) square.setOpacity(Math.min(1, 0.45 + n / 14400.0));
            Tooltip.install(square, new Tooltip(app.i18n.formatDate(start(d)) + " · " + app.i18n.formatDurationClock(n)));
            grid.add(square, days / 7, days % 7);
            days++;
        }
        box.getChildren().addAll(grid, label(app.i18n.t("activity.calendar.dailyAvg", app.i18n.formatDurationShort(seconds / days)), "muted"));
        return box;
    }

    private static VBox panel(String title) {
        VBox box = new VBox(12, label(title, "section-title"));
        box.getStyleClass().add("panel");
        return box;
    }

    private static Node metric(String name, String value) {
        return metric(name, label(value, "metric-value"));
    }

    private static Node metric(String name, Label value) {
        VBox box = new VBox(6, label(name, "muted"), value);
        box.getStyleClass().add("metric");
        return box;
    }

    private Node chart(String title, String[] labels, long[] values) {
        VBox box = panel(title);
        HBox bars = new HBox(8);
        bars.setAlignment(Pos.BOTTOM_CENTER);
        long max = Arrays.stream(values).max().orElse(0);
        for (int i = 0; i < values.length; i++) {
            Region bar = new Region();
            bar.getStyleClass().add(i == values.length - 1 ? "bar-last" : "bar");
            bar.setMaxWidth(44);
            bar.setPrefHeight(values[i] == 0 ? 2 : Math.max(3, 94.0 * values[i] / max));
            VBox col = new VBox(6, bar, label(labels[i], "muted"));
            col.setPrefWidth(1);
            col.setPrefHeight(119);
            col.setAlignment(Pos.BOTTOM_CENTER);
            HBox.setHgrow(col, Priority.ALWAYS);
            Tooltip.install(col, new Tooltip(labels[i] + " · " + app.i18n.formatDurationClock(values[i])));
            bars.getChildren().add(col);
        }
        box.getChildren().add(bars);
        return box;
    }
}
