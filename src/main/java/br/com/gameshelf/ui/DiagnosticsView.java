package br.com.gameshelf.ui;

import br.com.gameshelf.service.diagnostics.*;
import br.com.gameshelf.service.i18n.LocalizationService;
import br.com.gameshelf.service.update.AppVersion;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static br.com.gameshelf.ui.Ui.*;

/**
 * Technical Diagnostics view showing system info, platform detector details,
 * process monitoring, storage integrity, live performance, logs, and sanitized report export.
 */
public final class DiagnosticsView extends ScrollPane {
    private final Stage owner;
    private final DiagnosticsService service;
    private final AppLogger logger;
    private final LocalizationService i18n;
    private final Supplier<Boolean> monitorActiveSupplier;
    private final Supplier<String> lastScanSupplier;
    private final Supplier<Collection<String>> secretsSupplier;
    private final Consumer<String> statusNotifier;

    private final VBox content = new VBox(16);
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final VBox body = new VBox(16);
    private final Timeline perfTimeline;

    private Label heapCardValue, ramCardValue, cpuCardValue, uptimeCardValue;
    private Label perfHeapUsedLbl, perfRamLbl, perfCpuLbl, perfThreadsLbl, perfUptimeLbl;
    private TextArea logArea;
    private ComboBox<String> logLevelFilter;
    private TextField logSearch;
    private final Runnable logListener;
    private DiagnosticReport currentReport;

    public DiagnosticsView(
            Stage owner,
            DiagnosticsService service,
            AppLogger logger,
            LocalizationService i18n,
            Supplier<Boolean> monitorActiveSupplier,
            Supplier<String> lastScanSupplier,
            Supplier<Collection<String>> secretsSupplier,
            Consumer<String> statusNotifier
    ) {
        this.owner = owner;
        this.service = service;
        this.logger = logger;
        this.i18n = i18n;
        this.monitorActiveSupplier = monitorActiveSupplier;
        this.lastScanSupplier = lastScanSupplier;
        this.secretsSupplier = secretsSupplier;
        this.statusNotifier = statusNotifier;

        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("activity-scroll");
        content.getStyleClass().add("page");
        setContent(content);

        spinner.setMaxSize(20, 20);
        spinner.setVisible(false);
        spinner.setManaged(false);

        Label title = label(i18n.t("diag.title"), "page-title");
        Label subtitle = label(i18n.t("diag.subtitle"), "muted");
        subtitle.setWrapText(true);

        Button runAllBtn = button(i18n.t("diag.btn.runAll"), "refresh", "primary", this::runAllAsync);
        Button platformsBtn = button(i18n.t("diag.btn.scanPlatforms"), null, "quiet", this::runPlatformsAsync);
        Button dbBtn = button(i18n.t("diag.btn.checkDb"), "database", "quiet", this::runDbCheckAsync);
        Button copyBtn = button(i18n.t("diag.btn.copyReport"), "copy", "quiet", () -> openPreview(true));
        Button exportBtn = button(i18n.t("diag.btn.exportReport"), "export", "quiet", () -> openPreview(false));
        Button logsBtn = button(i18n.t("diag.btn.openLogs"), "folder", "quiet", this::openLogsFolder);
        Button bugBtn = button(i18n.t("diag.btn.reportBug"), "external", "quiet", () -> UpdateDialog.openUrl(AppVersion.REPO_ISSUES_NEW));

        HBox headerRow = row(10, title, spring(), spinner, runAllBtn);
        FlowPane tools = new FlowPane(8, 8, platformsBtn, dbBtn, copyBtn, exportBtn, logsBtn, bugBtn);
        content.getChildren().addAll(headerRow, subtitle, tools, body);

        this.logListener = () -> Platform.runLater(this::refreshLogsArea);
        this.logger.addListener(logListener);

        this.perfTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            if (owner == null || (owner.isShowing() && !owner.isIconified())) {
                refreshLivePerformance();
            }
        }));
        this.perfTimeline.setCycleCount(Timeline.INDEFINITE);
        this.perfTimeline.play();

        refreshSnapshot();
    }

    public void dispose() {
        perfTimeline.stop();
        logger.removeListener(logListener);
    }

    public void refreshSnapshot() {
        double v = getVvalue();
        currentReport = service.snapshot(monitorActiveSupplier.get(), lastScanSupplier.get());
        render(currentReport);
        Platform.runLater(() -> setVvalue(v));
    }

    private void runAllAsync() {
        runAsync(() -> service.runFullDiagnostics(monitorActiveSupplier.get(), lastScanSupplier.get()));
    }

    private void runPlatformsAsync() {
        runAsync(() -> {
            service.runPlatformCheck();
            return service.snapshot(monitorActiveSupplier.get(), lastScanSupplier.get());
        });
    }

    private void runDbCheckAsync() {
        runAsync(() -> {
            service.runIntegrityCheck();
            return service.snapshot(monitorActiveSupplier.get(), lastScanSupplier.get());
        });
    }

    private void runAsync(Supplier<DiagnosticReport> job) {
        spinner.setVisible(true);
        spinner.setManaged(true);
        Thread.ofPlatform().daemon().name("gameshelf-diag").start(() -> {
            DiagnosticReport r = job.get();
            Platform.runLater(() -> {
                spinner.setVisible(false);
                spinner.setManaged(false);
                currentReport = r;
                double v = getVvalue();
                render(r);
                Platform.runLater(() -> setVvalue(v));
            });
        });
    }

    private void render(DiagnosticReport report) {
        body.getChildren().clear();

        var perf = report.performance();
        var gen = report.general();
        heapCardValue = label(i18n.formatBytes(perf.jvmHeapUsedBytes()), "metric-value");
        ramCardValue = label(perf.processRamBytes() >= 0 ? i18n.formatBytes(perf.processRamBytes()) : i18n.t("common.unavailable"), "metric-value");
        cpuCardValue = label(perf.processCpuLoad() >= 0 ? i18n.formatDecimal(perf.processCpuLoad() * 100.0, 1) + "%" : i18n.t("common.unavailable"), "metric-value");
        Label dbCardValue = label("ok".equalsIgnoreCase(report.storage().integrityCheckResult()) ? i18n.t("diag.db.operational") : report.storage().integrityCheckResult(), "metric-value");
        Label monCardValue = label(i18n.t(report.processes().monitorActive() ? "diag.monitor.active" : "diag.monitor.unavailable"), "metric-value");
        uptimeCardValue = label(i18n.formatDurationClock(perf.uptimeMillis() / 1000L), "metric-value");

        FlowPane summaryCards = new FlowPane(10, 10,
                summaryMetric(i18n.t("diag.card.processRam"), "memory", ramCardValue),
                summaryMetric(i18n.t("diag.card.cpu"), "cpu", cpuCardValue),
                summaryMetric(i18n.t("diag.card.heap"), "memory", heapCardValue),
                summaryMetric(i18n.t("diag.card.db"), "database", dbCardValue),
                summaryMetric(i18n.t("diag.card.monitor"), "diagnostics", monCardValue),
                summaryMetric(i18n.t("diag.card.uptime"), "clock", uptimeCardValue)
        );
        body.getChildren().add(summaryCards);

        // 3.1 General Information
        VBox generalPanel = panel(i18n.t("diag.general.title"));
        generalPanel.getChildren().addAll(
                kv(i18n.t("diag.general.appVersion"), "v" + gen.appVersion()),
                kv(i18n.t("diag.general.java"), gen.javaVersion()),
                kv(i18n.t("diag.general.javafx"), gen.javafxVersion()),
                kv(i18n.t("diag.general.os"), gen.osName() + " " + gen.osVersion()),
                kv(i18n.t("diag.general.arch"), gen.osArch()),
                kv(i18n.t("diag.general.games"), "%s / %s / %s / %s".formatted(
                        i18n.formatInt(gen.totalGames()), i18n.formatInt(gen.autoDetectedGames()),
                        i18n.formatInt(gen.manualGames()), i18n.formatInt(gen.runningGames()))),
                kv(i18n.t("diag.general.lastScan"), gen.lastLibraryScanStatus().isBlank() ? "—" : gen.lastLibraryScanStatus())
        );
        body.getChildren().add(generalPanel);

        // 3.2 - 3.4 Platforms
        VBox platformsBox = new VBox(8, label(i18n.t("diag.platforms.title"), "section-title"));
        for (var p : report.platforms()) {
            platformsBox.getChildren().add(platformCard(p));
        }
        body.getChildren().add(platformsBox);

        // 3.5 Process Monitoring
        var procs = report.processes();
        VBox procPanel = panel(i18n.t("diag.processes.title"));
        procPanel.getChildren().addAll(
                kv(i18n.t("diag.card.monitor"), i18n.t(procs.monitorActive() ? "diag.monitor.active" : "diag.monitor.unavailable")),
                kv(i18n.t("diag.processes.interval"), procs.intervalSeconds() + " " + i18n.t("unit.second.short")),
                kv(i18n.t("diag.scanMs"),String.format("%.2f ms",procs.meanScanMillis())),
                kv(i18n.t("settings.offline"),Boolean.toString(procs.offline())),
                kv(i18n.t("diag.processes.scanned"), i18n.formatInt(procs.processesScanned())),
                kv(i18n.t("diag.processes.accessErrors"), i18n.formatInt(procs.accessErrors())),
                kv(i18n.t("diag.processes.lastSave"), procs.lastSaveAt() == null ? "—" : i18n.formatDateTime(procs.lastSaveAt())),
                kv(i18n.t("diag.processes.conflicts"), procs.conflicts().isEmpty() ? i18n.t("diag.none") : String.join(" | ", procs.conflicts()))
        );
        if (!procs.monitoredGames().isEmpty()) {
            Label sub = label(i18n.t("diag.processes.monitored", procs.monitoredGames().size()), "game-title");
            procPanel.getChildren().add(sub);
            for (var g : procs.monitoredGames()) {
                String pids = g.pids().isEmpty() ? "" : " · PID: " + g.pids();
                String statusStr = g.activeSession() && g.sessionStart() != null
                        ? i18n.t("diag.processes.activeSession", i18n.formatTimeWithSeconds(g.sessionStart())) + pids
                        : i18n.t("diag.processes.idle") + pids;
                procPanel.getChildren().add(kv(g.name() + " (" + i18n.platformName(g.platform()) + ")",
                        (g.targetExecutable().isBlank() ? "—" : g.targetExecutable()) + "  ·  " + statusStr));
            }
        }
        body.getChildren().add(procPanel);

        // 3.6 Database & Storage
        var st = report.storage();
        VBox storagePanel = panel(i18n.t("diag.storage.title"));
        storagePanel.getChildren().addAll(
                kv(i18n.t("diag.storage.location"), DiagnosticSanitizer.sanitize(st.databasePath())),
                kv(i18n.t("diag.storage.dbSize"), i18n.formatBytes(st.databaseSizeBytes())),
                kv(i18n.t("diag.storage.schema"), st.schemaVersion()),
                kv(i18n.t("diag.storage.games"), i18n.formatInt(st.gamesCount())),
                kv(i18n.t("diag.storage.sessions"), i18n.formatInt(st.sessionsCount())),
                kv(i18n.t("diag.storage.cacheSize"), i18n.formatBytes(st.imageCacheBytes())),
                kv(i18n.t("diag.storage.cacheLimit"), i18n.formatInt(st.imageCacheLimitMb()) + " MB"),
                kv(i18n.t("diag.storage.diskFree"), i18n.formatBytes(st.diskFreeBytes())),
                kv(i18n.t("diag.storage.integrity"), st.integrityCheckResult())
        );
        body.getChildren().add(storagePanel);

        // 3.7 Performance
        VBox perfPanel = panel(i18n.t("diag.perf.title"));
        perfHeapUsedLbl = label(i18n.formatBytes(perf.jvmHeapUsedBytes()), "muted");
        perfRamLbl = label(perf.processRamBytes() >= 0 ? i18n.formatBytes(perf.processRamBytes()) : i18n.t("common.unavailable"), "muted");
        perfCpuLbl = label(perf.processCpuLoad() >= 0 ? i18n.formatDecimal(perf.processCpuLoad() * 100.0, 1) + "%" : i18n.t("common.unavailable"), "muted");
        perfThreadsLbl = label(i18n.formatInt(perf.threadCount()), "muted");
        perfUptimeLbl = label(i18n.formatDurationClock(perf.uptimeMillis() / 1000L), "muted");
        perfPanel.getChildren().addAll(
                kvLabel(i18n.t("diag.perf.heapUsed"), perfHeapUsedLbl),
                kv(i18n.t("diag.perf.heapMax"), i18n.formatBytes(perf.jvmHeapMaxBytes())),
                kvLabel(i18n.t("diag.perf.processRam"), perfRamLbl),
                kvLabel(i18n.t("diag.perf.cpu"), perfCpuLbl),
                kvLabel(i18n.t("diag.perf.threads"), perfThreadsLbl),
                kv(i18n.t("diag.perf.detectionTime"), i18n.formatInt(perf.lastDetectionMs()) + " ms"),
                kvLabel(i18n.t("diag.perf.uptime"), perfUptimeLbl)
        );
        body.getChildren().add(perfPanel);

        // 3.8 Logs
        VBox logsPanel = panel(i18n.t("diag.logs.title"));
        logLevelFilter = new ComboBox<>();
        logLevelFilter.getItems().addAll(i18n.t("diag.logs.allLevels"), "INFO", "WARNING", "ERROR", "DEBUG");
        logLevelFilter.getSelectionModel().selectFirst();
        logLevelFilter.setOnAction(e -> refreshLogsArea());

        logSearch = new TextField();
        logSearch.setPromptText(i18n.t("diag.logs.searchPlaceholder"));
        logSearch.textProperty().addListener((o, a, b) -> refreshLogsArea());
        HBox.setHgrow(logSearch, Priority.ALWAYS);

        Button copyLogsBtn = button(i18n.t("diag.logs.copyVisible"), "copy", "quiet", () -> {
            copyToClipboard(logArea.getText());
            statusNotifier.accept(i18n.t("diag.logs.copied"));
        });

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setPrefRowCount(9);
        logArea.getStyleClass().addAll("mono", "log-box");

        logsPanel.getChildren().addAll(row(8, logLevelFilter, logSearch, copyLogsBtn), logArea);
        body.getChildren().add(logsPanel);
        refreshLogsArea();
    }

    private void refreshLivePerformance() {
        if (heapCardValue == null) return;
        var perf = service.measurePerformance();
        heapCardValue.setText(i18n.formatBytes(perf.jvmHeapUsedBytes()));
        ramCardValue.setText(perf.processRamBytes() >= 0 ? i18n.formatBytes(perf.processRamBytes()) : i18n.t("common.unavailable"));
        cpuCardValue.setText(perf.processCpuLoad() >= 0 ? i18n.formatDecimal(perf.processCpuLoad() * 100.0, 1) + "%" : i18n.t("common.unavailable"));
        uptimeCardValue.setText(i18n.formatDurationClock(perf.uptimeMillis() / 1000L));

        if (perfHeapUsedLbl != null) perfHeapUsedLbl.setText(heapCardValue.getText());
        if (perfRamLbl != null) perfRamLbl.setText(ramCardValue.getText());
        if (perfCpuLbl != null) perfCpuLbl.setText(cpuCardValue.getText());
        if (perfThreadsLbl != null) perfThreadsLbl.setText(i18n.formatInt(perf.threadCount()));
        if (perfUptimeLbl != null) perfUptimeLbl.setText(uptimeCardValue.getText());
    }

    private void refreshLogsArea() {
        if (logArea == null) return;
        AppLogger.Level lvl = switch (logLevelFilter.getSelectionModel().getSelectedIndex()) {
            case 1 -> AppLogger.Level.INFO;
            case 2 -> AppLogger.Level.WARNING;
            case 3 -> AppLogger.Level.ERROR;
            case 4 -> AppLogger.Level.DEBUG;
            default -> null;
        };
        var entries = logger.filter(lvl, logSearch.getText());
        if (entries.isEmpty()) {
            logArea.setText(i18n.t("diag.logs.empty"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (var e : entries) {
            sb.append(i18n.formatTimeWithSeconds(e.time())).append("  ")
                    .append(String.format("%-7s", e.level().name())).append("  ")
                    .append("[").append(e.service()).append("] ")
                    .append(e.message());
            if (!e.details().isBlank()) sb.append(" — ").append(e.details());
            sb.append('\n');
        }
        logArea.setText(sb.toString());
    }

    private VBox platformCard(PlatformDiagnostics.PlatformReport p) {
        Region dot = new Region();
        dot.getStyleClass().add("status-dot");
        dot.getStyleClass().add(switch (p.health()) {
            case SUCCESS -> "status-green";
            case WARNING -> "status-yellow";
            case ERROR -> "status-red";
            case INACTIVE -> "status-gray";
        });

        String summary = switch (p.detectorKind()) {
            case NOT_IMPLEMENTED -> i18n.t("diag.platforms.notImplemented");
            case EXPERIMENTAL -> p.launcherFound()
                    ? i18n.t("diag.platforms.experimental", p.gamesCount())
                    : i18n.t("diag.platforms.notInstalled");
            case IMPLEMENTED -> {
                if (!p.launcherFound()) yield i18n.t("diag.platforms.notInstalled");
                if (p.gamesCount() == 0) yield i18n.t("diag.platforms.noneFound");
                yield p.gamesCount() == 1 ? i18n.t("diag.platforms.detectedOne") : i18n.t("diag.platforms.detectedMany", p.gamesCount());
            }
        };

        Label sub = label(summary, switch (p.health()) {
            case SUCCESS -> "status-text-green";
            case WARNING -> "status-text-yellow";
            case ERROR -> "status-text-red";
            case INACTIVE -> "muted";
        });

        Label chevron = label("›", "muted");
        VBox titleCol = new VBox(2, label(i18n.platformName(p.canonicalName()), "game-title"), sub);
        HBox header = row(12, dot, titleCol, spring(), chevron);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-cursor: hand;");

        VBox details = new VBox(6);
        details.setVisible(false);
        details.setManaged(false);
        details.setPadding(new Insets(10, 0, 0, 21));
        details.getChildren().addAll(
                kv(i18n.t("diag.platforms.detectorStatus"), p.detectorKind().name()),
                kv(i18n.t("diag.platforms.launcherFound"), i18n.t(p.launcherFound() ? "diag.yes" : "diag.no")),
                kv(i18n.t("diag.platforms.gamesDetected"), i18n.t(p.gamesDetected() ? "diag.yes" : "diag.no")),
                kv(i18n.t("diag.platforms.gamesCount"), i18n.formatInt(p.gamesCount())),
                kv(i18n.t("diag.platforms.lastCheck"), i18n.formatDateTime(p.checkedAt())),
                kv(i18n.t("diag.platforms.duration"), i18n.formatInt(p.durationMs()) + " ms"),
                kv(i18n.t("diag.platforms.searchedDirs"), listOrNone(p.searchedDirs())),
                kv(i18n.t("diag.platforms.installPaths"), listOrNone(p.installPathsFound())),
                kv(i18n.t("diag.platforms.manifests"), listOrNone(p.manifestFilesFound())),
                kv(i18n.t("diag.platforms.warnings"), listOrNone(p.warnings())),
                kv(i18n.t("diag.platforms.errors"), listOrNone(p.errors()))
        );

        if (p.steamDetails() != null) {
            var s = p.steamDetails();
            details.getChildren().addAll(
                    new Separator(),
                    label(i18n.t("diag.platforms.steam.title"), "game-title"),
                    kv(i18n.t("diag.platforms.steam.roots"), listOrNone(s.mainRoots())),
                    kv(i18n.t("diag.platforms.steam.vdf"), listOrNone(s.vdfFiles())),
                    kv(i18n.t("diag.platforms.steam.libs"), listOrNone(s.libraryFolders())),
                    kv(i18n.t("diag.platforms.steam.acfs"), listOrNone(s.acfFiles())),
                    kv(i18n.t("diag.platforms.steam.appIds"), listOrNone(s.appIds())),
                    kv(i18n.t("diag.platforms.steam.invalid"), listOrNone(s.invalidManifests())),
                    kv(i18n.t("diag.platforms.steam.ignored"), ignoredOrNone(s.ignoredGames()))
            );
        }
        if (p.epicDetails() != null) {
            var e = p.epicDetails();
            details.getChildren().addAll(
                    new Separator(),
                    label(i18n.t("diag.platforms.epic.title"), "game-title"),
                    kv(i18n.t("diag.platforms.epic.dirs"), listOrNone(e.manifestDirs())),
                    kv(i18n.t("diag.platforms.epic.items"), listOrNone(e.itemFiles())),
                    kv(i18n.t("diag.platforms.epic.games"), listOrNone(e.gameNames())),
                    kv(i18n.t("diag.platforms.epic.exes"), listOrNone(e.executables())),
                    kv(i18n.t("diag.platforms.epic.invalid"), listOrNone(e.invalidManifests())),
                    kv(i18n.t("diag.platforms.epic.ignored"), ignoredOrNone(e.ignoredGames()))
            );
        }

        VBox card = new VBox(header, details);
        card.getStyleClass().add("panel");
        header.setOnMouseClicked(e -> {
            boolean show = !details.isVisible();
            details.setVisible(show);
            details.setManaged(show);
            chevron.setText(show ? "⌄" : "›");
        });
        return card;
    }

    private void openPreview(boolean copyMode) {
        DiagnosticReport report = currentReport != null ? currentReport
                : service.snapshot(monitorActiveSupplier.get(), lastScanSupplier.get());
        Collection<String> secrets = secretsSupplier.get();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("diag.preview.title"));
        var pane = dialog.getDialogPane();
        if (getScene() != null) pane.getStylesheets().setAll(getScene().getStylesheets());
        if (getParent() != null && getParent().getStyleClass().contains("light")) pane.getStyleClass().add("light");

        ComboBox<String> format = new ComboBox<>();
        format.getItems().addAll("TXT", "JSON");
        format.setValue("TXT");

        TextArea preview = new TextArea(report.toSanitizedText(i18n, secrets));
        preview.setEditable(false);
        preview.getStyleClass().add("mono");
        preview.setPrefWidth(680);
        preview.setPrefHeight(380);

        format.setOnAction(e -> preview.setText("JSON".equals(format.getValue())
                ? report.toSanitizedJson(secrets)
                : report.toSanitizedText(i18n, secrets)));

        Label note = label(i18n.t("diag.preview.note"), "muted");
        note.setWrapText(true);
        VBox box = new VBox(10, note, row(8, label(i18n.t("diag.preview.format"), "setting-name"), format), preview);
        pane.setContent(box);

        ButtonType primary = new ButtonType(
                i18n.t(copyMode ? "diag.preview.copy" : "diag.preview.save"),
                ButtonBar.ButtonData.OK_DONE
        );
        pane.getButtonTypes().setAll(ButtonType.CANCEL, primary);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == primary) {
            if (copyMode) {
                copyToClipboard(preview.getText());
                statusNotifier.accept(i18n.t("diag.reportCopied"));
            } else {
                boolean json = "JSON".equals(format.getValue());
                FileChooser fc = new FileChooser();
                fc.setTitle(i18n.t("diag.btn.exportReport"));
                fc.setInitialFileName(json ? "gameshelf-diagnostics.json" : "gameshelf-diagnostics.txt");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                        json ? "JSON" : "Text", json ? "*.json" : "*.txt"));
                File dest = fc.showSaveDialog(owner);
                if (dest != null) {
                    try {
                        Files.writeString(dest.toPath(), preview.getText(), StandardCharsets.UTF_8);
                        statusNotifier.accept(i18n.t("diag.reportSaved", dest.getName()));
                    } catch (Exception ex) {
                        statusNotifier.accept(i18n.t("status.error", ex.getMessage()));
                    }
                }
            }
        }
    }

    private void openLogsFolder() {
        try {
            Files.createDirectories(logger.logsDirectory());
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(logger.logsDirectory().toFile());
            }
        } catch (Exception ex) {
            statusNotifier.accept(i18n.t("status.error", ex.getMessage()));
        }
    }

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private String listOrNone(List<String> items) {
        return items == null || items.isEmpty() ? i18n.t("diag.none") : String.join("\n", items);
    }

    private String ignoredOrNone(List<PlatformDiagnostics.IgnoredEntry> items) {
        if (items == null || items.isEmpty()) return i18n.t("diag.none");
        StringJoiner j = new StringJoiner("\n");
        for (var item : items) j.add(item.name() + " — " + item.reason());
        return j.toString();
    }

    private static Node summaryMetric(String title, String iconName, Label value) {
        HBox top = row(6, icon(iconName), label(title, "muted"));
        VBox box = new VBox(6, top, value);
        box.getStyleClass().add("metric");
        box.setMinWidth(185);
        return box;
    }

    private static VBox panel(String title) {
        VBox box = new VBox(10, label(title, "section-title"));
        box.getStyleClass().add("panel");
        return box;
    }

    private static HBox kv(String key, String value) {
        return kvLabel(key, label(value, "muted"));
    }

    private static HBox kvLabel(String key, Label val) {
        Label k = label(key, "setting-name");
        k.setMinWidth(210);
        val.setWrapText(true);
        HBox.setHgrow(val, Priority.ALWAYS);
        HBox row = new HBox(12, k, val);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }
}
