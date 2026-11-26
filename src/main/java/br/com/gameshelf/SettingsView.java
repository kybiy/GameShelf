package br.com.gameshelf;

import br.com.gameshelf.service.i18n.LanguagePreference;
import br.com.gameshelf.service.update.AppVersion;
import br.com.gameshelf.service.update.UpdateChannel;
import br.com.gameshelf.ui.UpdateDialog;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static br.com.gameshelf.ui.Ui.*;

final class SettingsView {
    private SettingsView() {}

    static Node build(MainWindow app) {
        VBox page = new VBox(17, label(app.i18n.t("settings.title"), "page-title"));
        page.getStyleClass().add("page");

        VBox look = panel(app.i18n.t("settings.appearance"));

        ComboBox<LanguagePreference> language = new ComboBox<>();
        language.getItems().addAll(LanguagePreference.SYSTEM, LanguagePreference.PT_BR, LanguagePreference.EN_US);
        language.setValue(app.i18n.preference());
        language.setConverter(new StringConverter<>() {
            @Override
            public String toString(LanguagePreference pref) {
                if (pref == null) return "";
                return switch (pref) {
                    case SYSTEM -> app.i18n.t("settings.language.system");
                    case PT_BR -> app.i18n.t("settings.language.ptBR");
                    case EN_US -> app.i18n.t("settings.language.enUS");
                };
            }

            @Override
            public LanguagePreference fromString(String s) {
                return LanguagePreference.SYSTEM;
            }
        });
        language.setOnAction(e -> {
            LanguagePreference chosen = language.getValue();
            if (chosen != null && chosen != app.i18n.preference()) {
                app.applyLanguage(chosen);
            }
        });

        var accent = new ComboBox<String>();
        var colors = List.of("#929cff", "#63b7aa", "#da98c1");
        accent.getItems().addAll(
                app.i18n.t("settings.accent.lavender"),
                app.i18n.t("settings.accent.mint"),
                app.i18n.t("settings.accent.pink")
        );
        accent.getSelectionModel().select(Math.max(0, colors.indexOf(app.library.setting("accent", colors.getFirst()))));
        accent.setOnAction(e -> {
            app.prefer("accent", colors.get(accent.getSelectionModel().getSelectedIndex()));
            app.applyTheme();
        });

        ComboBox<String> size = new ComboBox<>();
        size.getItems().addAll(
                app.i18n.t("settings.coverSize.small"),
                app.i18n.t("settings.coverSize.medium"),
                app.i18n.t("settings.coverSize.large")
        );
        var widths = List.of("134", "176", "222");
        size.getSelectionModel().select(Math.max(0, widths.indexOf(app.library.setting("coverSize", "176"))));
        var preview = new br.com.gameshelf.ui.Poster(1);
        Runnable resizePreview = () -> {
            double w = Integer.parseInt(widths.get(size.getSelectionModel().getSelectedIndex())) * 0.26;
            preview.setMinSize(w, w * 1.5);
            preview.setMaxSize(w, w * 1.5);
        };
        resizePreview.run();
        size.setOnAction(e -> {
            app.prefer("coverSize", widths.get(size.getSelectionModel().getSelectedIndex()));
            resizePreview.run();
        });

        look.getChildren().addAll(
                line(app.i18n.t("settings.language"), language),
                line(app.i18n.t("settings.lightTheme"), toggle(app, "light", false, app::applyTheme)),
                line(app.i18n.t("settings.accent"), accent),
                line(app.i18n.t("settings.coverSize"), row(10, preview, size)),
                line(app.i18n.t("settings.animations"), toggle(app, "animations", true, () -> {})),
                line(app.i18n.t("settings.compactMenu"), toggle(app, "compact", false, app::sidebar))
        );

        VBox library = panel(app.i18n.t("settings.library"));
        CheckBox tray = toggle(app, "tray", true, () -> {});
        tray.setDisable(!app.tray.available());
        library.getChildren().addAll(
                line(app.i18n.t("settings.scanOnStart"), toggle(app, "scanOnStart", true, () -> {})),
                line(app.i18n.t("settings.tray"), tray),
                line(app.i18n.t("settings.steamArtwork"), button(app.i18n.t("settings.retryArtwork"), "refresh", "quiet", () -> {
                    app.art.retry();
                    app.shelfArtworkChanged();
                    app.message(app.i18n.t("settings.artworkRetried"));
                }))
        );

        VBox updates = panel(app.i18n.t("update.section"));
        ComboBox<UpdateChannel> channel = new ComboBox<>();
        channel.getItems().addAll(UpdateChannel.STABLE, UpdateChannel.EXPERIMENTAL);
        channel.setValue(app.updateChannel());
        channel.setConverter(new StringConverter<>() {
            @Override
            public String toString(UpdateChannel ch) {
                if (ch == null) return "";
                return app.i18n.t(ch == UpdateChannel.STABLE ? "update.channel.stable" : "update.channel.experimental");
            }

            @Override
            public UpdateChannel fromString(String s) {
                return UpdateChannel.EXPERIMENTAL;
            }
        });
        channel.setOnAction(e -> {
            if (channel.getValue() != null) app.prefer("update.channel", channel.getValue().code());
        });

        Instant lastCheck = app.lastUpdateCheckInstant();
        Label lastCheckValue = label(lastCheck == null ? app.i18n.t("update.lastCheck.never") : app.i18n.formatDateTime(lastCheck), "muted");
        Button checkNowBtn = button(app.i18n.t("update.checkNow"), "update", "quiet", null);
        checkNowBtn.setOnAction(e -> {
            checkNowBtn.setDisable(true);
            checkNowBtn.setText(app.i18n.t("update.checking"));
            app.checkUpdatesAsync(true, res -> {
                checkNowBtn.setDisable(false);
                checkNowBtn.setText(app.i18n.t("update.checkNow"));
                Instant refreshed = app.lastUpdateCheckInstant();
                lastCheckValue.setText(refreshed == null ? app.i18n.t("update.lastCheck.never") : app.i18n.formatDateTime(refreshed));
            });
        });
        Button restoreIgnored = button(app.i18n.t("update.restoreIgnored"), null, "quiet", app::restoreIgnoredUpdates);

        updates.getChildren().addAll(
                line(app.i18n.t("update.auto"), toggle(app, "update.auto", true, () -> {})),
                line(app.i18n.t("update.channel"), channel),
                line(app.i18n.t("update.lastCheck"), lastCheckValue),
                new FlowPane(8, 8, checkNowBtn, restoreIgnored)
        );

        VBox data = panel(app.i18n.t("settings.localData"));
        ComboBox<Integer> limit = new ComboBox<>();
        limit.getItems().addAll(64, 128, 256, 512);
        limit.setValue(Integer.valueOf(app.library.setting("cacheMb", "256")));
        limit.setOnAction(e -> {
            app.prefer("cacheMb", limit.getValue().toString());
            app.art.limit(limit.getValue());
        });
        data.getChildren().addAll(
                line(app.i18n.t("settings.cacheLimit"), limit),
                button(app.i18n.t("settings.clearCache"), null, "quiet", () -> app.work(() -> {
                    app.art.trim(0);
                    javafx.application.Platform.runLater(() -> {
                        app.art.retry();
                        app.message(app.i18n.t("settings.cacheCleared"));
                    });
                }))
        );
        FlowPane exports = new FlowPane(8, 8,
                button(app.i18n.t("settings.backupDb"), "folder", "quiet", () -> export(app, "db")),
                button(app.i18n.t("settings.exportGames"), null, "quiet", () -> export(app, "games")),
                button(app.i18n.t("settings.exportHistory"), null, "quiet", () -> export(app, "sessions"))
        );
        data.getChildren().add(exports);

        VBox catalog = panel(app.i18n.t("settings.steamGridDb"));
        PasswordField key = new PasswordField();
        key.setPromptText(app.i18n.t("settings.steamGridDb.prompt"));
        key.setText(app.library.setting("gridDbKey", ""));
        HBox.setHgrow(key, Priority.ALWAYS);
        Label note = label(app.i18n.t("settings.steamGridDb.note"), "muted");
        note.setWrapText(true);
        catalog.getChildren().addAll(
                note,
                row(8, key, button(app.i18n.t("settings.steamGridDb.save"), null, "quiet", () -> {
                    app.prefer("gridDbKey", key.getText().strip());
                    app.message(app.i18n.t("settings.steamGridDb.saved"));
                }))
        );

        page.getChildren().addAll(look, library, updates, catalog, data, button(app.i18n.t("settings.exit"), null, "quiet", app::exit));
        return scroll(page);
    }

    private static void export(MainWindow app, String type) {
        FileChooser fc = new FileChooser();
        boolean db = type.equals("db");
        fc.setTitle(app.i18n.t("settings.saveCopyTitle"));
        fc.setInitialFileName(db ? "gameshelf-backup.db" : "gameshelf-" + type + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                app.i18n.t(db ? "settings.filter.sqlite" : "settings.filter.csv"),
                db ? "*.db" : "*.csv"
        ));
        File dest = fc.showSaveDialog(app.stage);
        if (dest == null) return;
        app.work(() -> {
            if (db) {
                app.library.backup(dest.toPath());
            } else {
                try (var out = Files.newBufferedWriter(dest.toPath(), StandardCharsets.UTF_8)) {
                    if (type.equals("games")) {
                        out.write(app.i18n.t("settings.csv.gamesHeader"));
                        out.newLine();
                        for (Game g : app.library.snapshot())
                            csv(out, g.id(), g.name(), g.platform(), g.installed(), g.favorite(), g.seconds());
                    } else {
                        out.write(app.i18n.t("settings.csv.sessionsHeader"));
                        out.newLine();
                        for (Session s : app.library.history(null))
                            csv(out, s.gameId(), Instant.ofEpochSecond(s.start()), Instant.ofEpochSecond(s.end()), s.seconds());
                    }
                }
            }
            javafx.application.Platform.runLater(() -> app.message(app.i18n.t("settings.copySaved", dest)));
        });
    }

    private static void csv(BufferedWriter out, Object... fields) throws IOException {
        StringJoiner line = new StringJoiner(",");
        for (Object f : fields) line.add("\"" + f.toString().replace("\"", "\"\"") + "\"");
        out.write(line.toString());
        out.newLine();
    }

    static Node about(MainWindow app) {
        VBox page = new VBox(18, label(app.i18n.t("about.title"), "page-title"));
        page.getStyleClass().add("page");

        VBox card = panel(app.i18n.t("app.name"));
        Label summary = label(app.i18n.t("about.summary"), "muted");
        summary.setWrapText(true);
        Button checkBtn = button(app.i18n.t("about.checkUpdates"), "update", "primary", null);
        checkBtn.setOnAction(e -> {
            checkBtn.setDisable(true);
            checkBtn.setText(app.i18n.t("update.checking"));
            app.checkUpdatesAsync(true, res -> {
                checkBtn.setDisable(false);
                checkBtn.setText(app.i18n.t("about.checkUpdates"));
            });
        });
        FlowPane updateActions = new FlowPane(8, 8, checkBtn);
        if (app.availableUpdate() != null) {
            updateActions.getChildren().add(button(
                    app.i18n.t("about.openUpdate", app.availableUpdate().tagName()),
                    "external", "quiet", () -> app.showUpdateDialog(app.availableUpdate())
            ));
        }
        card.getChildren().addAll(summary, label(app.i18n.t("about.tech", AppVersion.CURRENT), "muted"), updateActions);
        page.getChildren().add(card);

        VBox support = panel(app.i18n.t("about.detection"));
        Label note = label(app.i18n.t("about.detection.note"), "muted");
        note.setWrapText(true);
        support.getChildren().add(note);
        page.getChildren().add(support);

        VBox links = panel(app.i18n.t("about.links"));
        FlowPane linkButtons = new FlowPane(8, 8,
                button(app.i18n.t("about.link.releases"), "external", "quiet", () -> UpdateDialog.openUrl(AppVersion.REPO_RELEASES_WEB)),
                button(app.i18n.t("about.link.privacy"), "external", "quiet", () -> UpdateDialog.openUrl(AppVersion.REPO_PRIVACY_URL)),
                button(app.i18n.t("about.link.signing"), "external", "quiet", () -> UpdateDialog.openUrl(AppVersion.REPO_CODE_SIGNING_URL))
        );
        links.getChildren().add(linkButtons);
        page.getChildren().add(links);
        return scroll(page);
    }

    private static Node scroll(VBox page) {
        var s = new ScrollPane(page);
        s.setFitToWidth(true);
        s.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return s;
    }

    private static HBox line(String name, Node control) {
        Label l = label(name, "setting-name");
        l.setMinWidth(Region.USE_PREF_SIZE);
        return row(12, l, spring(), control);
    }

    private static VBox panel(String title) {
        VBox v = new VBox(14, label(title, "section-title"));
        v.getStyleClass().add("panel");
        return v;
    }

    private static CheckBox toggle(MainWindow app, String key, boolean defaultValue, Runnable after) {
        CheckBox c = new CheckBox();
        c.setAccessibleText(key);
        c.setSelected(app.flag(key, defaultValue));
        c.setOnAction(e -> {
            app.prefer(key, String.valueOf(c.isSelected()));
            after.run();
        });
        return c;
    }
}
