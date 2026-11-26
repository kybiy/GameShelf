package br.com.gameshelf;

import br.com.gameshelf.service.i18n.LocalizationService;
import javafx.application.Platform;
import java.awt.*;
import java.awt.image.BufferedImage;

final class DesktopTray {
    private TrayIcon tray;
    private MenuItem showItem, quitItem;

    static BufferedImage logo() {
        var im = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        var g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(129, 140, 248));
        g.fillRoundRect(0, 0, 48, 48, 12, 12);
        g.setColor(new Color(24, 26, 44));
        g.fillRoundRect(9, 12, 6, 25, 2, 2);
        g.fillRoundRect(18, 12, 6, 25, 2, 2);
        g.rotate(-0.28, 31, 25);
        g.fillRoundRect(29, 11, 6, 25, 2, 2);
        g.dispose();
        return im;
    }

    void install(LocalizationService i18n, Runnable open, Runnable exit) {
        if (!SystemTray.isSupported()) return;
        try {
            var menu = new PopupMenu();
            showItem = new MenuItem(i18n.t("tray.open"));
            quitItem = new MenuItem(i18n.t("tray.exit"));
            showItem.addActionListener(e -> Platform.runLater(open));
            quitItem.addActionListener(e -> Platform.runLater(exit));
            menu.add(showItem);
            menu.add(quitItem);
            tray = new TrayIcon(logo(), i18n.t("app.name"), menu);
            tray.setImageAutoSize(true);
            tray.addActionListener(e -> Platform.runLater(open));
            SystemTray.getSystemTray().add(tray);
        } catch (Exception e) {
            tray = null;
        }
    }

    void updateTexts(LocalizationService i18n) {
        if (tray == null) return;
        if (showItem != null) showItem.setLabel(i18n.t("tray.open"));
        if (quitItem != null) quitItem.setLabel(i18n.t("tray.exit"));
    }

    boolean available() {
        return tray != null;
    }

    void close() {
        if (tray != null) SystemTray.getSystemTray().remove(tray);
    }
}
