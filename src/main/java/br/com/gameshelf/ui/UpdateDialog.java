package br.com.gameshelf.ui;

import br.com.gameshelf.service.i18n.LocalizationService;
import br.com.gameshelf.service.update.AppVersion;
import br.com.gameshelf.service.update.ReleaseInfo;
import br.com.gameshelf.service.update.VersionComparator;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static br.com.gameshelf.ui.Ui.*;

/** Modern update notification dialog matching GameShelf's dark/light theme. */
public final class UpdateDialog {
    public enum Action { DOWNLOAD, GITHUB, LATER, IGNORE }

    private UpdateDialog() {}

    public static void show(Stage owner,
                            List<String> stylesheets,
                            boolean lightTheme,
                            String accentCss,
                            boolean animations,
                            LocalizationService i18n,
                            ReleaseInfo release,
                            Consumer<String> onIgnore) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(i18n.t("app.name") + " — " + i18n.t("update.dialog.title"));
        dialog.setResizable(false);
        if (owner != null && !owner.getIcons().isEmpty()) {
            dialog.getIcons().setAll(owner.getIcons());
        }

        VBox card = buildContent(i18n, release, action -> {
            switch (action) {
                case DOWNLOAD -> openUrl(release.safeDownloadUrl());
                case GITHUB -> openUrl(release.hasOfficialHtmlUrl() ? release.htmlUrl() : AppVersion.REPO_RELEASES_WEB);
                case IGNORE -> {
                    if (onIgnore != null) onIgnore.accept(release.version());
                    dialog.close();
                }
                case LATER -> dialog.close();
            }
        });

        StackPane wrapper = new StackPane(card);
        wrapper.getStyleClass().add("root");
        if (lightTheme) wrapper.getStyleClass().add("light");
        if (accentCss != null && !accentCss.isBlank()) wrapper.setStyle(accentCss);
        wrapper.setPadding(new Insets(18));

        Scene scene = new Scene(wrapper);
        if (stylesheets != null) scene.getStylesheets().setAll(stylesheets);
        dialog.setScene(scene);

        if (animations) {
            card.setOpacity(0);
            card.setScaleX(0.97);
            card.setScaleY(0.97);
            dialog.setOnShown(e -> {
                Duration dur = Duration.millis(180);
                FadeTransition fade = new FadeTransition(dur, card);
                fade.setFromValue(0);
                fade.setToValue(1);
                ScaleTransition scale = new ScaleTransition(dur, card);
                scale.setFromX(0.97);
                scale.setFromY(0.97);
                scale.setToX(1);
                scale.setToY(1);
                ParallelTransition pt = new ParallelTransition(fade, scale);
                pt.setInterpolator(Interpolator.EASE_OUT);
                pt.play();
            });
        }
        dialog.showAndWait();
    }

    /** Builds the update dialog card content (also reusable for snapshot testing). */
    public static VBox buildContent(LocalizationService i18n, ReleaseInfo release, Consumer<Action> onAction) {
        VBox card = new VBox(14);
        card.getStyleClass().add("panel");
        card.setPrefWidth(540);
        card.setMaxWidth(540);

        StackPane heroIcon = new StackPane(icon("update"));
        heroIcon.getStyleClass().add("update-hero-icon");

        Label title = label(i18n.t("update.dialog.title"), "section-title");
        Label subtitle = label(i18n.t("update.dialog.subtitle"), "muted");
        VBox titleBox = new VBox(2, title, subtitle);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Label stageBadge = stagePill(i18n, release.stage());
        HBox header = row(12, heroIcon, titleBox, stageBadge);

        VBox currentCol = new VBox(3,
                label(i18n.t("update.dialog.installed"), "muted"),
                label("v" + AppVersion.CURRENT, "game-title"));
        VBox nextCol = new VBox(3,
                label(i18n.t("update.dialog.latest"), "muted"),
                label(release.tagName(), "accent-text"));
        nextCol.getChildren().get(1).getStyleClass().add("game-title");
        HBox.setHgrow(currentCol, Priority.ALWAYS);
        HBox.setHgrow(nextCol, Priority.ALWAYS);
        HBox versions = new HBox(24, currentCol, nextCol);

        VBox metaBox = new VBox(3);
        if (release.publishedAt() != null) {
            metaBox.getChildren().add(label(i18n.t("update.dialog.published", i18n.formatDateTime(release.publishedAt())), "muted"));
        }
        String sizes = formatSizes(i18n, release);
        if (!sizes.isEmpty()) {
            Label sizeLbl = label(i18n.t("update.dialog.size", sizes), "muted");
            sizeLbl.setWrapText(true);
            metaBox.getChildren().add(sizeLbl);
        }

        Label notesHeader = label(i18n.t("update.dialog.notes"), "game-title");
        String bodyText = release.body() == null || release.body().isBlank()
                ? i18n.t("update.dialog.noNotes")
                : release.body().strip();
        TextArea notesArea = new TextArea(bodyText);
        notesArea.setEditable(false);
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(7);
        notesArea.setFocusTraversable(false);

        Button download = button(i18n.t("update.dialog.download"), "update", "primary", () -> onAction.accept(Action.DOWNLOAD));
        download.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(download, Priority.ALWAYS);

        Button later = button(i18n.t("update.dialog.later"), null, "quiet", () -> onAction.accept(Action.LATER));
        HBox primaryRow = row(10, download, later);

        Button github = button(i18n.t("update.dialog.viewGitHub"), "external", "subtle-link", () -> onAction.accept(Action.GITHUB));
        Button ignore = button(i18n.t("update.dialog.ignore"), null, "subtle-link", () -> onAction.accept(Action.IGNORE));

        HBox secondaryRow = new HBox(12, github, spring(), ignore);
        secondaryRow.setAlignment(Pos.CENTER);

        card.getChildren().add(header);
        card.getChildren().add(new Separator());
        card.getChildren().add(versions);
        if (!metaBox.getChildren().isEmpty()) card.getChildren().add(metaBox);
        card.getChildren().addAll(notesHeader, notesArea, primaryRow);
        release.preferredPortable().ifPresent(portable -> {
            if (release.preferredInstaller().isPresent()) {
                Button portableLink = button(
                        i18n.t("update.dialog.downloadPortable") + " (" + i18n.formatBytes(portable.size()) + ")",
                        null, "subtle-link", () -> openUrl(portable.downloadUrl()));
                HBox pRow = new HBox(portableLink);
                pRow.setAlignment(Pos.CENTER);
                card.getChildren().add(pRow);
            }
        });
        card.getChildren().add(secondaryRow);
        return card;
    }

    private static Label stagePill(LocalizationService i18n, VersionComparator.Stage stage) {
        String key = switch (stage) {
            case ALPHA -> "update.stage.alpha";
            case BETA -> "update.stage.beta";
            case STABLE -> "update.stage.stable";
        };
        String css = switch (stage) {
            case ALPHA -> "badge-alpha";
            case BETA -> "badge-beta";
            case STABLE -> "badge-stable";
        };
        Label badge = label(i18n.t(key), "badge-pill");
        badge.getStyleClass().add(css);
        return badge;
    }

    private static String formatSizes(LocalizationService i18n, ReleaseInfo release) {
        List<String> parts = new ArrayList<>();
        release.preferredInstaller().ifPresent(a -> parts.add(a.name() + " (" + i18n.formatBytes(a.size()) + ")"));
        release.preferredPortable().ifPresent(a -> parts.add(a.name() + " (" + i18n.formatBytes(a.size()) + ")"));
        return String.join("  ·  ", parts);
    }

    public static void openUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
        }
    }
}
