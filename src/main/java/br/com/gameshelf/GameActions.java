package br.com.gameshelf;

import br.com.gameshelf.ui.Artwork;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import static br.com.gameshelf.ui.Ui.*;

final class GameActions {
    private final MainWindow app;

    GameActions(MainWindow app) {
        this.app = app;
    }

    Alert alert(Alert.AlertType type, String title, String body) {
        Alert a = new Alert(type, body);
        a.setTitle(app.i18n.t("app.name"));
        a.setHeaderText(title);
        style(a);
        return a;
    }

    private void style(Dialog<?> dialog) {
        dialog.initOwner(app.stage);
        var pane = dialog.getDialogPane();
        pane.getStylesheets().setAll(app.root.getScene().getStylesheets());
        if (app.flag("light", false)) pane.getStyleClass().add("light");
        pane.setStyle(app.root.getStyle());
    }

    private static MenuItem item(String title, Runnable action) {
        MenuItem i = new MenuItem(title);
        i.setOnAction(e -> action.run());
        return i;
    }

    ContextMenu menu(Game g) {
        MenuItem play = item(app.i18n.t("actions.menu.play"), () -> launch(g));
        play.setDisable(!g.installed() || app.tracker.sessions().containsKey(g.id()));
        var menu = new ContextMenu(
                play,
                item(app.i18n.t("actions.menu.details"), () -> app.details(g)),
                item(app.i18n.t(g.favorite() ? "activity.fav.remove" : "activity.fav.add"), () -> favorite(g)),
                new SeparatorMenuItem(),
                item(app.i18n.t("actions.menu.changeCover"), () -> chooseArt(g, Artwork.Kind.COVER)),
                item(app.i18n.t("actions.menu.changeBanner"), () -> chooseArt(g, Artwork.Kind.BANNER)),
                item(app.i18n.t("actions.menu.searchGridDb"), () -> searchArt(g)),
                item(app.i18n.t("actions.menu.monitorOther"), () -> monitor(g))
        );
        if (!g.monitorOverride().isBlank())
            menu.getItems().add(item(app.i18n.t("actions.menu.restoreMonitor"), () -> {
                app.library.monitor(g.id(), "");
                app.changed();
            }));
        if (g.manual())
            menu.getItems().add(item(app.i18n.t("actions.menu.remove"), () -> remove(g)));
        return menu;
    }

    void launch(Game g) {
        app.work(() -> Launcher.open(g, app.library.setting("args." + g.id(), ""), app.library.setting("work." + g.id(), ""), app.i18n));
    }

    void favorite(Game g) {
        app.library.favorite(g.id());
        app.changed();
    }

    private Path file(String title, String description, String... patterns) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, patterns));
        File f = fc.showOpenDialog(app.stage);
        return f == null ? null : f.toPath().toAbsolutePath().normalize();
    }

    private Path picture(String title) {
        return file(title, app.i18n.t("actions.art.images"), "*.jpg", "*.jpeg", "*.png");
    }

    private void searchArt(Game g) {
        String key = app.library.setting("gridDbKey", "");
        if (key.isBlank()) {
            alert(Alert.AlertType.INFORMATION, app.i18n.t("actions.griddb.noKeyTitle"), app.i18n.t("actions.griddb.noKeyBody")).showAndWait();
            return;
        }
        TextInputDialog search = new TextInputDialog(g.name());
        search.setHeaderText(app.i18n.t("actions.griddb.searchHeader"));
        style(search);
        search.showAndWait().filter(query -> !query.isBlank()).ifPresent(query -> app.work(() -> {
            GridDb service = new GridDb(key);
            var found = service.search(query);
            javafx.application.Platform.runLater(() -> {
                if (found.isEmpty()) {
                    app.message(app.i18n.t("actions.griddb.noMatches"));
                    return;
                }
                ChoiceDialog<GridDb.Match> choice = new ChoiceDialog<>(found.getFirst(), found);
                style(choice);
                choice.setHeaderText(app.i18n.t("actions.griddb.selectEdition", g.name()));
                choice.showAndWait().ifPresent(match -> {
                    String coverLabel = app.i18n.t("actions.griddb.cover");
                    String bannerLabel = app.i18n.t("actions.griddb.banner");
                    var type = new ChoiceDialog<>(coverLabel, List.of(coverLabel, bannerLabel));
                    style(type);
                    type.setHeaderText(app.i18n.t("actions.griddb.imageType"));
                    type.showAndWait().ifPresent(value -> {
                        boolean cover = value.equals(coverLabel);
                        app.work(() -> {
                            var images = service.images(match.id(), cover);
                            javafx.application.Platform.runLater(() -> selectArt(g, cover ? Artwork.Kind.COVER : Artwork.Kind.BANNER, images));
                        });
                    });
                });
            });
        }));
    }

    private void selectArt(Game g, Artwork.Kind kind, List<GridDb.Art> images) {
        if (images.isEmpty()) {
            app.message(app.i18n.t("actions.griddb.noImages"));
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        style(dialog);
        dialog.setTitle(app.i18n.t("settings.steamGridDb"));
        dialog.setHeaderText(app.i18n.t("actions.griddb.chooseImage", g.name()));
        ComboBox<GridDb.Art> choices = new ComboBox<>();
        choices.getItems().setAll(images);
        choices.getSelectionModel().selectFirst();
        var preview = new StackPane();
        boolean cover = kind == Artwork.Kind.COVER;
        preview.setPrefSize(cover ? 180 : 400, cover ? 270 : 160);
        Runnable display = () -> preview.getChildren().setAll(new br.com.gameshelf.ui.Poster(g, kind, choices.getValue().url(), app.art));
        choices.setOnAction(e -> display.run());
        display.run();
        dialog.getDialogPane().setContent(new VBox(8, choices, preview));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            app.library.settingValue((cover ? "cover." : "banner.") + g.id(), choices.getValue().url());
            app.shelfArtworkChanged();
            app.changed();
        }
    }

    private void chooseArt(Game g, Artwork.Kind kind) {
        Path p = picture(app.i18n.t(kind == Artwork.Kind.COVER ? "actions.art.changeCover" : "actions.art.changeBanner"));
        if (p == null) return;
        app.library.settingValue((kind == Artwork.Kind.COVER ? "cover." : "banner.") + g.id(), p.toString());
        app.shelfArtworkChanged();
        app.changed();
    }

    private void monitor(Game g) {
        Path p = file(app.i18n.t("actions.monitor.selectTitle"), app.i18n.t("actions.monitor.exeFilter"), "*.exe");
        if (p != null) {
            app.library.monitor(g.id(), p.toString());
            app.changed();
        }
    }

    private void remove(Game g) {
        if (alert(Alert.AlertType.CONFIRMATION, app.i18n.t("actions.remove.title", g.name()), app.i18n.t("actions.remove.body"))
                .showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            app.library.remove(g.id());
            app.changed();
        }
    }

    private static TextField field(String value) {
        return new TextField(value);
    }

    private static void setPath(TextField field, Path p) {
        if (p != null) field.setText(p.toString());
    }

    private HBox picker(TextField field, Runnable action) {
        HBox.setHgrow(field, Priority.ALWAYS);
        return row(6, field, tool(app.i18n.t("actions.file.choose"), "folder", action));
    }

    void add(Path dropped) {
        Path executable = dropped == null ? file(app.i18n.t("actions.add.title"), app.i18n.t("actions.add.filterDesc"), "*.exe", "*.lnk") : dropped;
        if (executable == null) return;
        Path absolute = executable.toAbsolutePath().normalize();
        String path = absolute.toString(), filename = absolute.getFileName().toString();
        String extension = filename.toLowerCase(Locale.ROOT);
        if (!Files.isRegularFile(absolute) || !(extension.endsWith(".exe") || extension.endsWith(".lnk"))) {
            alert(Alert.AlertType.WARNING, app.i18n.t("actions.add.invalidTitle"), app.i18n.t("actions.add.invalidBody")).showAndWait();
            return;
        }
        if (app.library.snapshot().stream().anyMatch(g -> g.executable().equalsIgnoreCase(path))) {
            alert(Alert.AlertType.INFORMATION, app.i18n.t("actions.add.duplicateTitle"), app.i18n.t("actions.add.duplicateBody")).showAndWait();
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(app.i18n.t("actions.add.title"));
        dialog.setHeaderText(app.i18n.t("actions.add.header"));
        style(dialog);
        ButtonType add = new ButtonType(app.i18n.t("actions.add.title"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, add);

        TextField name = field(filename.substring(0, filename.lastIndexOf('.')));
        TextField hours = field("0"), monitor = field(""), cover = field(""), banner = field("");
        TextField folder = field(absolute.getParent().toString()), workDir = field(absolute.getParent().toString());
        TextArea args = new TextArea();
        args.setPromptText(app.i18n.t("actions.add.argsPrompt"));
        args.setPrefRowCount(2);

        ComboBox<String> platform = new ComboBox<>();
        platform.getItems().setAll(MainWindow.PLATFORMS);
        platform.setConverter(new StringConverter<>() {
            @Override
            public String toString(String canonical) {
                return canonical == null ? "" : app.i18n.platformName(canonical);
            }

            @Override
            public String fromString(String s) {
                return "Local";
            }
        });
        platform.setValue("Local");

        Label error = label("", "error");
        error.setWrapText(true);
        Label target = label(path, "muted");
        target.setWrapText(true);

        VBox advanced = new VBox(6,
                label(app.i18n.t("actions.add.installFolder"), "muted"), folder,
                label(app.i18n.t("actions.add.workDir"), "muted"), workDir,
                label(app.i18n.t("actions.add.args"), "muted"), args,
                label(app.i18n.t("actions.add.monitoredExe"), "muted"),
                picker(monitor, () -> setPath(monitor, file(app.i18n.t("actions.add.selectExe"), app.i18n.t("actions.monitor.exeFilter"), "*.exe"))),
                label(app.i18n.t("actions.add.cover"), "muted"),
                picker(cover, () -> setPath(cover, picture(app.i18n.t("actions.add.selectCover")))),
                label(app.i18n.t("actions.add.banner"), "muted"),
                picker(banner, () -> setPath(banner, picture(app.i18n.t("actions.add.selectBanner")))),
                label(app.i18n.t("actions.add.initialHours"), "muted"), hours
        );
        var extra = new TitledPane(app.i18n.t("actions.add.extraOptions"), advanced);
        extra.setExpanded(false);
        extra.setAnimated(false);

        VBox form = new VBox(8, target, label(app.i18n.t("actions.add.gameName"), "muted"), name,
                label(app.i18n.t("actions.add.platform"), "muted"), platform, extra, error);
        form.setPadding(new Insets(6));
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(430);
        scroll.setMaxHeight(400);
        dialog.getDialogPane().setContent(scroll);

        String id = "local:" + UUID.randomUUID();
        dialog.getDialogPane().lookupButton(add).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                if (name.getText().isBlank()) throw new IllegalArgumentException(app.i18n.t("actions.add.err.name"));
                double initial = Double.parseDouble(hours.getText().strip().replace(',', '.'));
                if (!Double.isFinite(initial) || initial < 0 || initial > 1_000_000)
                    throw new IllegalArgumentException(app.i18n.t("actions.add.err.hours"));
                for (TextField dir : List.of(folder, workDir))
                    if (!Files.isDirectory(Path.of(dir.getText())))
                        throw new IllegalArgumentException(app.i18n.t("actions.add.err.dir"));
                for (TextField f : List.of(monitor, cover, banner))
                    if (!f.getText().isBlank() && !Files.isRegularFile(Path.of(f.getText())))
                        throw new IllegalArgumentException(app.i18n.t("actions.add.err.file", f.getText()));
                app.library.add(new Game(id, name.getText().strip(), platform.getValue(), folder.getText(), path, "", true, monitor.getText(), Math.round(initial * 3600), false));
                app.library.settingValue("args." + id, args.getText());
                app.library.settingValue("work." + id, workDir.getText());
                app.library.settingValue("cover." + id, cover.getText());
                app.library.settingValue("banner." + id, banner.getText());
            } catch (IllegalArgumentException ex) {
                error.setText(ex instanceof NumberFormatException ? app.i18n.t("actions.add.err.hoursFormat") : ex.getMessage());
                e.consume();
            }
        });
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == add) {
            app.changed();
            app.details(app.library.get(id));
        }
    }
}
