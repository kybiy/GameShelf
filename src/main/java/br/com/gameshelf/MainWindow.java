package br.com.gameshelf;

import br.com.gameshelf.service.diagnostics.AppLogger;
import br.com.gameshelf.service.diagnostics.DiagnosticsService;
import br.com.gameshelf.service.i18n.LanguagePreference;
import br.com.gameshelf.service.i18n.LocalizationService;
import br.com.gameshelf.service.update.AppVersion;
import br.com.gameshelf.service.update.ReleaseInfo;
import br.com.gameshelf.service.update.UpdateChannel;
import br.com.gameshelf.service.update.UpdateService;
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
import javafx.scene.shape.Circle;
import javafx.stage.*;
import javafx.util.Duration;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
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
    final AppLogger logger;
    final DiagnosticsService diagnosticsService;
    final UpdateService updateService = new UpdateService();
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
    private final BooleanProperty updateBadgeVisible = new SimpleBooleanProperty(false);
    private final Map<String, Button> navigation = new HashMap<>();
    private final List<Runnable> i18nUpdaters = new ArrayList<>();

    private ActivityView activity;
    private DiagnosticsView diagnosticsView;
    private String page = "Todos os jogos", gameId;
    private Set<String> playing = Set.of();
    private boolean scanning, closing, monitorOk = true, checkingUpdates;
    private String scanStatus;
    private int lastDetectedCount = -1;
    private List<String> lastScanWarnings = List.of();
    private String lastScanError;
    private ReleaseInfo availableUpdate;

    private final Path showRequest;
    MainWindow(Stage stage, Library library, Path data) {
        showRequest=data.resolve("show-window.request");
        this.stage = stage;
        this.library = library;
        this.i18n = new LocalizationService(LanguagePreference.fromCode(library.setting("language", "system")));
        this.tracker = new Playtime(library);
        NetworkPolicy.offline(Boolean.parseBoolean(library.setting("offline","false")));
        this.art = new Artwork(data);
        this.art.limit(Integer.parseInt(library.setting("cacheMb", "256")));
        this.logger = new AppLogger(data);
        this.logger.info("App", "GameShelf initialized (v" + AppVersion.CURRENT + ")");
        this.diagnosticsService = new DiagnosticsService(library, tracker, art, logger, PLATFORMS);
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
        stage.showingProperty().addListener((o,a,b)->art.background(!b||stage.isIconified()));
        stage.iconifiedProperty().addListener((o,a,b)->art.background(b||!stage.isShowing()));
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
                if(Files.deleteIfExists(showRequest))Platform.runLater(()->{stage.show();stage.setIconified(false);stage.toFront();});
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

        if (flag("update.auto", true)) {
            monitor.schedule(() -> checkUpdatesAsync(false, null), 2, TimeUnit.SECONDS);
        }
    }

    boolean flag(String key, boolean fallback) {
        return Boolean.parseBoolean(library.setting(key, Boolean.toString(fallback)));
    }

    void prefer(String key, String value) {
        library.settingValue(key, value);
        save();
    }

    private static double linear(double c){return c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);}
    void applyTheme() {
        root.getStyleClass().remove("light");
        if (flag("light", false)) root.getStyleClass().add("light");
        themeCaption();
        String accent=library.setting("accent","#929cff");
        if(!accent.matches("#[0-9a-fA-F]{6}"))accent="#929cff";
        var c=javafx.scene.paint.Color.web(accent);
        double y=.2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue());
        root.setStyle("-shelf-accent: "+accent+"; -shelf-on-accent: "+(y>.179?"black":"white")+";");
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
                ActivityView target = activity;
                if (target != null) Platform.runLater(() -> target.setVvalue(v));
                return;
            }
        }

        if (root.getCenter() == shelf) {
            shelf.refreshLanguage();
        } else if (page.equals("Estatísticas") && activity != null) {
            double v = activity.getVvalue();
            activity = new ActivityView(this, null);
            ActivityView target = activity;
            root.setCenter(activity);
            Platform.runLater(() -> target.setVvalue(v));
        } else if (page.equals("Diagnóstico") && diagnosticsView != null) {
            double v = diagnosticsView.getVvalue();
            diagnosticsView.dispose();
            diagnosticsView = buildDiagnosticsView();
            DiagnosticsView target = diagnosticsView;
            root.setCenter(diagnosticsView);
            Platform.runLater(() -> target.setVvalue(v));
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

    UpdateChannel updateChannel() {
        return UpdateChannel.fromCode(library.setting("update.channel", ""), AppVersion.CURRENT);
    }

    ReleaseInfo availableUpdate() {
        return availableUpdate;
    }

    void checkUpdatesAsync(boolean manual, Consumer<UpdateService.CheckResult> onFinished) {
        if (closing || checkingUpdates) return;
        checkingUpdates = true;
        work(() -> {
            UpdateService.CheckResult result;
            try {
                result = updateService.check(updateChannel(), library.setting("update.ignored", ""), manual);
            } finally {
                checkingUpdates = false;
            }
            Platform.runLater(() -> {
                if (closing) return;
                if (result.checkedAt() != null && (result.status() == UpdateService.Status.UP_TO_DATE
                        || result.status() == UpdateService.Status.UPDATE_AVAILABLE)) {
                    prefer("update.lastCheck", Long.toString(result.checkedAt().getEpochSecond()));
                }
                if (result.status() == UpdateService.Status.UPDATE_AVAILABLE && result.update().isPresent()) {
                    availableUpdate = result.update().get();
                    updateBadgeVisible.set(true);
                    message(i18n.t("update.availableStatus", availableUpdate.tagName()));
                    if (manual || stage.isShowing()) {
                        showUpdateDialog(availableUpdate);
                    }
                } else if (result.status() == UpdateService.Status.UP_TO_DATE) {
                    availableUpdate = null;
                    updateBadgeVisible.set(false);
                    if (manual) {
                        actions.alert(Alert.AlertType.INFORMATION, i18n.t("update.section"),
                                i18n.t("update.upToDate", "v" + AppVersion.CURRENT)).showAndWait();
                    }
                } else if (manual) {
                    String key = result.status() == UpdateService.Status.RATE_LIMITED ? "update.rateLimited" : "update.error";
                    actions.alert(Alert.AlertType.WARNING, i18n.t("update.section"), i18n.t(key)).showAndWait();
                }
                if (onFinished != null) onFinished.accept(result);
            });
        });
    }

    void showUpdateDialog(ReleaseInfo release) {
        if (release == null) return;
        UpdateDialog.show(
                stage,
                root.getScene().getStylesheets(),
                flag("light", false),
                root.getStyle(),
                flag("animations", true),
                i18n,
                release,
                ignoredVer -> {
                    prefer("update.ignored", ignoredVer);
                    availableUpdate = null;
                    updateBadgeVisible.set(false);
                }
        );
    }

    void restoreIgnoredUpdates() {
        prefer("update.ignored", "");
        message(i18n.t("update.ignoredRestored"));
    }

    Instant lastUpdateCheckInstant() {
        String raw = library.setting("update.lastCheck", "");
        if (raw.isBlank()) return null;
        try {
            return Instant.ofEpochSecond(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void themeCaption() {
        if (stage.isShowing()) WindowsCaption.apply(stage.getTitle(), flag("light", false), library.setting("accent", "#929cff"));
    }

    String page() {
        return page;
    }

    void navigate(String name) {
        if (diagnosticsView != null && !name.equals("Diagnóstico")) {
            diagnosticsView.dispose();
            diagnosticsView = null;
        }
        Button prev = navigation.get(page);
        if (prev != null) prev.getStyleClass().remove("selected");
        page = name;
        gameId = null;
        activity = null;
        Button next = navigation.get(page);
        if (next != null) next.getStyleClass().add("selected");
        switch (name) {
            case "Diagnóstico" -> {
                diagnosticsView = buildDiagnosticsView();
                root.setCenter(diagnosticsView);
            }
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

    private DiagnosticsView buildDiagnosticsView() {
        return new DiagnosticsView(
                stage,
                diagnosticsService,
                logger,
                i18n,
                () -> monitorOk,
                () -> scanStatus,
                () -> {
                    String k = library.setting("gridDbKey", "");
                    return k.isBlank() ? List.of() : List.of(k);
                },
                this::message
        );
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
        nav(links, "Todos os jogos", "library", () -> i18n.t("nav.allGames"), false);
        nav(links, "Favoritos", "heart", () -> i18n.t("nav.favorites"), false);
        nav(links, "Recentes", "clock", () -> i18n.t("nav.recent"), false);
        nav(links, "Estatísticas", "chart", () -> i18n.t("nav.stats"), false);

        group(links, "nav.section.platforms");
        for (String p : PLATFORMS) {
            nav(links, p, p.equals("Local") ? "folder" : "platform", () -> i18n.platformName(p), false);
        }

        ScrollPane scrolling = new ScrollPane(links);
        scrolling.setFitToWidth(true);
        scrolling.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrolling, Priority.ALWAYS);

        VBox bottom = new VBox(3);
        bottom.getStyleClass().add("side-bottom");
        nav(bottom, "Diagnóstico", "diagnostics", () -> i18n.t("nav.diagnostics"), false);
        nav(bottom, "Configurações", "settings", () -> i18n.t("nav.settings"), false);
        nav(bottom, "Sobre", "info", () -> i18n.t("nav.about"), true);

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

    private void nav(VBox box, String name, String glyph, Supplier<String> titleSupplier, boolean withUpdateDot) {
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
        Node graphicNode;
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
            graphicNode = monogram;
        } else {
            graphicNode = b.getGraphic();
        }
        if (withUpdateDot && graphicNode != null) {
            Circle dot = new Circle(3.5);
            dot.getStyleClass().add("update-dot");
            dot.visibleProperty().bind(updateBadgeVisible);
            dot.managedProperty().bind(updateBadgeVisible);
            StackPane badged = new StackPane(graphicNode, dot);
            StackPane.setAlignment(dot, Pos.TOP_RIGHT);
            graphicNode = badged;
        }
        b.setGraphic(graphicNode);
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
                logger.info("Detector", "Library scan finished: " + lastDetectedCount + " detected");
                for (String w : lastScanWarnings) logger.warn("Detector", w);
            } catch (Exception e) {
                lastScanError = e.getMessage();
                logger.error("Detector", "Library scan failed", e);
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
        if (diagnosticsView != null) diagnosticsView.dispose();
        tray.close();
        art.close();
        monitor.shutdownNow();
        worker.shutdown();
        worker.awaitTermination(6, TimeUnit.SECONDS);
        monitor.awaitTermination(3, TimeUnit.SECONDS);
        library.save();
    }
}
