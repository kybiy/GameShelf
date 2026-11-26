package br.com.gameshelf;

import javafx.application.Platform;
import java.awt.*;
import java.awt.image.BufferedImage;

final class DesktopTray {
    private TrayIcon tray;
    static BufferedImage logo() {
        var im=new BufferedImage(48,48,BufferedImage.TYPE_INT_ARGB);
        var g=im.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(129,140,248));g.fillRoundRect(0,0,48,48,12,12);
        g.setColor(new Color(24,26,44));g.fillRoundRect(9,12,6,25,2,2);g.fillRoundRect(18,12,6,25,2,2);
        g.rotate(-.28,31,25);g.fillRoundRect(29,11,6,25,2,2);g.dispose();return im;
    }
    void install(Runnable open,Runnable exit) {
        if(!SystemTray.isSupported())return;
        try{
            var menu=new PopupMenu();var show=new MenuItem("Abrir GameShelf");var quit=new MenuItem("Sair do GameShelf");
            show.addActionListener(e->Platform.runLater(open));quit.addActionListener(e->Platform.runLater(exit));
            menu.add(show);menu.add(quit);tray=new TrayIcon(logo(),"GameShelf",menu);tray.setImageAutoSize(true);
            tray.addActionListener(e->Platform.runLater(open));SystemTray.getSystemTray().add(tray);
        }catch(Exception e){tray=null;}
    }
    boolean available(){return tray!=null;}
    void close(){if(tray!=null)SystemTray.getSystemTray().remove(tray);}
}
