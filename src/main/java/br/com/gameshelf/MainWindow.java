package br.com.gameshelf;

import br.com.gameshelf.service.i18n.LanguagePreference;
import br.com.gameshelf.service.i18n.LocalizationService;
import br.com.gameshelf.ui.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static br.com.gameshelf.ui.Ui.*;

public final class MainWindow {
    static final List<String> PLATFORMS = List.of(
            "Steam", "Epic Games", "Riot Games", "Xbox", "GOG",
            "Ubisoft Connect", "EA App", "Battle.net", "Rockstar Games", "Local", "Outros"
    );

    final Stage stage;
    final Library library;
    final Playtime tracker;
    final Artwork art;
    final LocalizationService i18n;
    final GameActions actions;
    final BorderPane root = new BorderPane();
    final DesktopTray tray = new DesktopTray();

    private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("gameshelf-monitor").factory());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("gameshelf-library").factory());

    private final Label status;
    private final Label monitorLabel;
    private final LibraryView shelf;
    private final BooleanProperty compactMenu = new SimpleBooleanProperty();
    private final Map<String, Button> navigation = new HashMap<>();
    private final List<Runnable> i18nUpdaters = new ArrayList<>();

    private ActivityView activity;
    private String page = "Todos os jogos", gameId;
    private Set<String> playing = Set.of();
    private boolean scanning, closing, monitorOk = true;
    private String scanStatus;
    private int lastDetectedCount = -1;
    private List<String> lastScanWarnings = List.of();
    private String lastScanError;

    MainWindow(Stage stage, Library library, Path data) {
        this.stage = stage;
        this.library = library;
        this.i18n = new LocalizationService(LanguagePreference.fromCode(library.setting("language", "system")));
        this.tracker = new Playtime(library);
        this.art = new Artwork(data);
        this.art.limit(Integer.parseInt(library.setting("cacheMb", "256")));
        this.status = label(i18n.t("status.preparing"), "muted");
        this.monitorLabel = label(i18n.t("monitor.active"), "monitor-label");
        this.scanStatus = i18n.t("status.localLibrary");
        this.actions = new GameActions(this);
        this.shelf = new LibraryView(this);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/gameshelf.css")).toExternalForm());
        stage.setScene(scene);
        stage.setTitle(i18n.t("app.name"));

        var icon = DesktopTray.logo();
        var fxIcon = new WritableImage(48, 48);
        for (int y = 0; y < 48; y++)
            for (int x = 0; x < 48; x++)
                fxIcon.getPixelWriter().setArgb(x, y, icon.getRGB(x, y));
        stage.getIcons().add(fxIcon);

        var bounds = Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(1280, bounds.getWidth()));
        stage.setHeight(Math.min(820, bounds.getHeight()));
        stage.setMinWidth(Math.min(720, bounds.getWidth()));
        stage.setMinHeight(Math.min(510, bounds.getHeight()));

        stage.widthProperty().addListener((o, old, width) -> {
            if ((old.doubleValue() < 940) != (width.doubleValue() < 940)
                    && library.setting("compact", "auto").equals("auto")) {
                sidebar();
            }
        });
        stage.focusedProperty().addListener((o, was, focused) -> {
            if (focused) {
                if (root.getCenter() == shelf) shelf.refresh();
                else if (activity != null) activity.update();
            }
        });

        status.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(status, Priority.ALWAYS);
        HBox footer = row(10, status, monitorLabel);
        footer.getStyleClass().add("footer");
        root.setBottom(footer);

        stage.setOnCloseRequest(e -> {
            e.consume();
            if (tray.available() && flag("tray", true)) stage.hide();
            else exit();
        });
        stage.setOnShown(e -> Platform.runLater(this::themeCaption));

        applyTheme();
        sidebar();
        navigate(page);
    }

    void show() {
        tray.install(i18n, () -> {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
        }, this::exit);
        Platform.setImplicitExit(false);
        stage.show();
        stage.centerOnScreen();
        monitor.scheduleWithFixedDelay(() -> {
            try {
                tracker.poll();
                Platform.runLater(() -> tick(true));
            } catch (Exception e) {
                Platform.runLater(() -> tick(false));
            }
        }, 0, 2, TimeUnit.SECONDS);
        monitor.scheduleWithFixedDelay(() -> {
            try {
                library.save();
            } catch (Exception e) {
                report(e);
            }
        }, 5, 5, TimeUnit.SECONDS);
        if (flag("scanOnStart", true)) scan();
        else status.setText(scanStatus);
    }

    boolean flag(String key, boolean fallback) {
        return Boolean.parseBoolean(library.setting(key, Boolean.toString(fallback)));
    }

    void prefer(String key, String value) {
        library.settingValue(key, value);
        save();
    }

    void applyTheme() {
        root.getStyleClass().remove("light");
        if (flag("light", false)) root.getStyleClass().add("light");
        themeCaption();
        root.setStyle("-shelf-accent: " + library.setting("accent", "#929cff") + ";");
    }

    void applyLanguage(LanguagePreference pref) {
        prefer("language", pref.code());
        i18n.setPreference(pref);
        i18nUpdaters.forEach(Runnable::run);
        tray.updateTexts(i18n);
        monitorLabel.setText(i18n.t(monitorOk ? "monitor.active" : "monitor.unavailable"));
        refreshScanStatusText();
        status.setText(scanStatus);
        status.setTooltip(new Tooltip(scanStatus));

        if (gameId != null) {
            Game g = library.get(gameId);
            if (g != null) {
                double v = activity != null ? activity.getVvalue() : 0;
                showDetails(g);
                if (activity != null) Platform.runLater(() -> activity.setVvalue(v));
                return;
            }
        }

        if (root.getCenter() == shelf) {
            shelf.refreshLanguage();
        } else if (page.equals("Estatísticas") && activity != null) {
            double v = activity.getVvalue();
            activity = new ActivityView(this, null);
            root.setCenter(activity);
            Platform.runLater(() -> activity.setVvalue(v));
        } else if (page.equals("Configurações") || page.equals("Sobre")) {
            double v = root.getCenter() instanceof ScrollPane sp ? sp.getVvalue() : 0;
            root.setCenter(page.equals("Configurações") ? SettingsView.build(this) : SettingsView.about(this));
            if (root.getCenter() instanceof ScrollPane sp) {
                Platform.runLater(() -> sp.setVvalue(v));
            }
        }
    }

    private void refreshScanStatusText() {
        if (scanning) {
            scanStatus = i18n.t("status.scanning");
        } else if (lastScanError != null) {
            scanStatus = i18n.t("status.updateFailed", lastScanError);
        } else if (lastDetectedCount >= 0) {
            scanStatus = lastScanWarnings.isEmpty()
                    ? i18n.t("status.updated", lastDetectedCount)
                    : String.join("  ·  ", lastScanWarnings);
        } else {
            scanStatus = i18n.t("status.localLibrary");
        }
    }

    private void themeCaption() {
        if (stage.isShowing()) WindowsCaption.apply(stage.getTitle(), flag("light", false), library.setting("accent", "#929cff"));
    }

    String page() {
        return page;
    }

    void navigate(String name) {
        Button prev = navigation.get(page);
        if (prev != null) prev.getStyleClass().remove("selected");
        page = name;
        gameId = null;
        activity = null;
        Button next = navigation.get(page);
        if (next != null) next.getStyleClass().add("selected");
        switch (name) {
            case "Configurações" -> root.setCenter(SettingsView.build(this));
            case "Sobre" -> root.setCenter(SettingsView.about(this));
            case "Estatísticas" -> {
                activity = new ActivityView(this, null);
                root.setCenter(activity);
            }
            default -> {
                root.setCenter(shelf);
                shelf.open(name);
            }
        }
    }

    void details(Game g) {
        showDetails(g);
        enter();
    }

    private void showDetails(Game g) {
        gameId = g.id();
        activity = new ActivityView(this, g);
        root.setCenter(activity);
    }

    private ParallelTransition entry;

    private void enter() {
        if (entry != null) {
            entry.stop();
            Node old = entry.getNode();
            old.setOpacity(1);
            old.setScaleX(1);
            old.setScaleY(1);
        }
        if (!flag("animations", true)) return;
        Node target = root.getCenter();
        Duration duration = Duration.millis(180);
        FadeTransition fade = new FadeTransition(duration);
        fade.setFromValue(0.65);
        fade.setToValue(1);
        ScaleTransition zoom = new ScaleTransition(duration);
        zoom.setFromX(0.985);
        zoom.setFromY(0.985);
        zoom.setToX(1);
        zoom.setToY(1);
        entry = new ParallelTransition(target, fade, zoom);
        entry.setInterpolator(Interpolator.EASE_OUT);
        entry.play();
    }

    void back() {
        navigate(page);
        enter();
    }

    void sidebar() {
        compactMenu.set(flag("compact", stage.getWidth() < 940));
        if (root.getLeft() != null) return;

        VBox links = new VBox(3);
        var logo = icon("library");
        StackPane mark = new StackPane(logo);
        mark.getStyleClass().add("brand-mark");
        HBox brand = row(11, mark);
        Label wordmark = label(i18n.t("app.name"), "brand");
        expandedOnly(wordmark);
        brand.getChildren().add(wordmark);
        brand.getStyleClass().add("brand-row");
        VBox top = new VBox(brand);

        group(links, "nav.section.library");
        nav(links, "Todos os jogos", "library", () -> i18n.t("nav.allGames"));
        nav(links, "Favoritos", "heart", () -> i18n.t("nav.favorites"));
        nav(links, "Recentes", "clock", () -> i18n.t("nav.recent"));
        nav(links, "Estatísticas", "chart", () -> i18n.t("nav.stats"));

        group(links, "nav.section.platforms");
        for (String p : PLATFORMS) {
            nav(links, p, p.equals("Local") ? "folder" : "platform", () -> i18n.platformName(p));
        }

        ScrollPane scrolling = new ScrollPane(links);
        scrolling.setFitToWidth(true);
        scrolling.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrolling, Priority.ALWAYS);

        VBox bottom = new VBox(3);
        bottom.getStyleClass().add("side-bottom");
        nav(bottom, "Configurações", "settings", () -> i18n.t("nav.settings"));
        nav(bottom, "Sobre", "info", () -> i18n.t("nav.about"));

        StringProperty collapseText = new SimpleStringProperty(i18n.t("nav.collapse"));
        StringProperty expandText = new SimpleStringProperty(i18n.t("nav.expand"));
        Button collapse = button(collapseText.get(), "menu", "nav", () -> {
            prefer("compact", Boolean.toString(!compactMenu.get()));
            sidebar();
        });
        collapse.textProperty().bind(Bindings.when(compactMenu).then("").otherwise(collapseText));
        Tooltip hint = new Tooltip();
        hint.textProperty().bind(Bindings.when(compactMenu).then(expandText).otherwise(collapseText));
        collapse.setTooltip(hint);
        i18nUpdaters.add(() -> {
            collapseText.set(i18n.t("nav.collapse"));
            expandText.set(i18n.t("nav.expand"));
        });
        bottom.getChildren().add(collapse);

        VBox side = new VBox(top, scrolling, bottom);
        side.getStyleClass().add("sidebar");
        side.prefWidthProperty().bind(Bindings.when(compactMenu).then(70).otherwise(205));
        side.setMinWidth(Region.USE_PREF_SIZE);
        root.setLeft(side);
    }

    private void expandedOnly(Node node) {
        node.visibleProperty().bind(compactMenu.not());
        node.managedProperty().bind(node.visibleProperty());
    }

    private void group(VBox box, String textKey) {
        Separator separator = new Separator();
        separator.visibleProperty().bind(compactMenu);
        separator.managedProperty().bind(compactMenu);
        VBox.setMargin(separator, new Insets(12, 4, 7, 4));
        Label title = label(i18n.t(textKey), "section-label");
        expandedOnly(title);
        VBox.setMargin(title, new Insets(17, 0, 5, 10));
        i18nUpdaters.add(() -> title.setText(i18n.t(textKey)));
        box.getChildren().addAll(separator, title);
    }

    private void nav(VBox box, String name, String glyph, Supplier<String> titleSupplier) {
        String initialTitle = titleSupplier.get();
        Button b = button(initialTitle, glyph, "nav", () -> {
            navigate(name);
            enter();
        });
        ScaleTransition press = new ScaleTransition(Duration.millis(85), b);
        b.armedProperty().addListener((o, was, armed) -> {
            press.stop();
            double scale = armed ? 0.98 : 1;
            if (flag("animations", true)) {
                press.setToX(scale);
                press.setToY(scale);
                press.play();
            } else {
                b.setScaleX(1);
                b.setScaleY(1);
            }
        });
        if (glyph.equals("platform")) {
            String code = switch (name) {
                case "Epic Games" -> "EP";
                case "Riot Games" -> "RI";
                case "Battle.net" -> "B";
                case "Rockstar Games" -> "R★";
                case "Ubisoft Connect" -> "U";
                case "Outros" -> "···";
                default -> name.substring(0, 1);
            };
            Label monogram = label(code, "platform-mark");
            monogram.getStyleClass().add("store-" + PLATFORMS.indexOf(name));
            b.setGraphic(monogram);
        }
        navigation.put(name, b);

        StringProperty titleProp = new SimpleStringProperty(initialTitle);
        b.textProperty().bind(Bindings.when(compactMenu).then("").otherwise(titleProp));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAccessibleText(initialTitle);
        Tooltip tip = new Tooltip(initialTitle);
        b.tooltipProperty().bind(Bindings.when(compactMenu).then(tip).otherwise((Tooltip) null));
        i18nUpdaters.add(() -> {
            String updated = titleSupplier.get();
            titleProp.set(updated);
            tip.setText(updated);
            b.setAccessibleText(updated);
        });
        box.getChildren().add(b);
    }

    void changed() {
        save();
        if (gameId != null) {
            Game g = library.get(gameId);
            if (g != null) showDetails(g);
            else back();
        } else if (root.getCenter() == shelf) {
            shelf.refresh();
        }
    }

    private void tick(boolean ok) {
        if (closing) return;
        monitorOk = ok;
        monitorLabel.setText(i18n.t(ok ? "monitor.active" : "monitor.unavailable"));
        var active = tracker.sessions().keySet();
        if (stage.isShowing() && !stage.isIconified()) {
            if (activity != null && (!active.isEmpty() || !playing.isEmpty())) activity.tick(!playing.equals(active));
            if (root.getCenter() == shelf && (!playing.equals(active) || !active.isEmpty())) shelf.refresh();
            playing = Set.copyOf(active);
        }
    }

    void scan() {
        if (scanning || closing) return;
        scanning = true;
        status.setText(i18n.t("status.scanning"));
        shelf.scanning(true);
        work(() -> {
            try {
                Detector.Result result = Detector.scan();
                library.merge(result.games(), result.warnings().isEmpty());
                library.save();
                lastDetectedCount = result.games().size();
                lastScanWarnings = result.warnings();
                lastScanError = null;
            } catch (Exception e) {
                lastScanError = e.getMessage();
            } finally {
                Platform.runLater(() -> {
                    scanning = false;
                    refreshScanStatusText();
                    shelf.scanning(false);
                    status.setText(scanStatus);
                    status.setTooltip(new Tooltip(scanStatus));
                    shelf.refresh();
                    if (activity != null) activity.update();
                });
            }
        });
    }

    interface Work {
        void run() throws Exception;
    }

    void work(Work job) {
        if (!closing) worker.execute(() -> {
            try {
                job.run();
            } catch (Exception e) {
                report(e);
            }
        });
    }

    void save() {
        work(library::save);
    }

    void shelfArtworkChanged() {
        shelf.invalidate();
    }

    void report(Exception e) {
        Platform.runLater(() -> {
            status.setText(i18n.t("status.error", e.getMessage()));
            status.setTooltip(new Tooltip(status.getText()));
        });
    }

    void message(String text) {
        status.setText(text);
    }

    void exit() {
        Alert a = actions.alert(Alert.AlertType.CONFIRMATION, i18n.t("exit.confirm.title"), i18n.t("exit.confirm.body"));
        if (a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) Platform.exit();
    }

    void stop() throws Exception {
        closing = true;
        tray.close();
        art.close();
        monitor.shutdownNow();
        worker.shutdown();
        worker.awaitTermination(6, TimeUnit.SECONDS);
        monitor.awaitTermination(3, TimeUnit.SECONDS);
        library.save();
    }
}
