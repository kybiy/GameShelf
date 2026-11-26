package br.com.gameshelf.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.*;

public final class Ui {
    private Ui() {}
    public static Label label(String text,String style) {
        Label l=new Label(text); l.getStyleClass().add(style); return l;
    }
    public static Region spring() {
        Region space=new Region(); HBox.setHgrow(space,Priority.ALWAYS); return space;
    }
    public static HBox row(double gap, Node... nodes) {
        HBox row=new HBox(gap,nodes); row.setAlignment(Pos.CENTER_LEFT); return row;
    }
    public static Button button(String title,String icon,String style,Runnable action) {
        Button b=new Button(title,icon==null?null:icon(icon));
        b.getStyleClass().add(style); b.setOnAction(e->action.run()); return b;
    }
    public static Button tool(String title,String icon,Runnable action) {
        Button b=button("",icon,"tool",action); b.setTooltip(new Tooltip(title)); b.setAccessibleText(title); return b;
    }
    public static Node icon(String name) {
        String path=switch(name) {
            case "library" -> "M3 4 L7 4 L7 20 L3 20 Z M10 4 L13 4 L13 20 L10 20 Z M15 6 L18 5 L22 19 L19 20 Z";
            case "heart" -> "M12 20 C8 17 2 13 2 8 C2 3 9 2 12 7 C15 2 22 3 22 8 C22 13 16 18 12 20 Z";
            case "clock" -> "M12 2 A10 10 0 1 1 11.99 2 M12 6 L12 12 L16 15";
            case "chart" -> "M4 20 L4 12 L7 12 L7 20 Z M10 20 L10 4 L13 4 L13 20 Z M16 20 L16 8 L19 8 L19 20 Z";
            case "menu" -> "M3 4 L21 4 L21 20 L3 20 Z M9 4 L9 20";
            case "search" -> "M10 3 A7 7 0 1 1 9.99 3 M15 15 L21 21";
            case "add" -> "M12 4 L12 20 M4 12 L20 12";
            case "refresh" -> "M20 9 C16 1 5 2 3 11 M20 3 L20 9 L14 9 M4 15 C8 23 19 22 21 13 M4 21 L4 15 L10 15";
            case "grid" -> "M3 3 H10 V10 H3 Z M14 3 H21 V10 H14 Z M3 14 H10 V21 H3 Z M14 14 H21 V21 H14 Z";
            case "list" -> "M3 5 H5 M9 5 H21 M3 12 H5 M9 12 H21 M3 19 H5 M9 19 H21";
            case "back" -> "M20 12 H4 M11 5 L4 12 L11 19";
            case "play" -> "M6 3 L21 12 L6 21 Z";
            case "more" -> "M4 12 h1 M11 12 h1 M18 12 h1";
            case "settings" -> "M3 7 H10 M15 7 H21 M3 17 H7 M12 17 H21 M12 4 A3 3 0 1 1 11.99 4 M9 14 A3 3 0 1 1 8.99 14";
            case "info" -> "M12 2 A10 10 0 1 1 11.99 2 M12 11 V17 M12 7 v1";
            case "game" -> "M7 6 C3 6 2 12 2 16 C2 21 6 19 9 15 H15 C18 19 22 21 22 16 C22 12 21 6 17 6 Z M5 10 H10 M7.5 8 V13 M16 10 h1 M18 12 h1";
            case "folder" -> "M2 6 H9 L11 8 H22 L20 19 H2 Z";
            case "filter" -> "M3 5 H21 M6 12 H18 M9 19 H15";
            case "diagnostics" -> "M3 12 H7 L10 5 L14 19 L17 12 H21";
            case "update" -> "M12 3 V14 M7 10 L12 15 L17 10 M4 17 V20 H20 V17";
            case "globe" -> "M12 2 A10 10 0 1 1 11.99 2 M2 12 H22 M12 2 C15 6 15 18 12 22 C9 18 9 6 12 2";
            case "check" -> "M4 12 L9 17 L20 6";
            case "warning" -> "M12 3 L21 19 H3 Z M12 9 V13 M12 16 v1";
            case "copy" -> "M9 9 H19 V21 H9 Z M5 15 H4 V4 H15 V5";
            case "export" -> "M12 15 V4 M7 9 L12 4 L17 9 M4 16 V20 H20 V16";
            case "external" -> "M14 4 H20 V10 M20 4 L11 13 M10 6 H5 V19 H18 V14";
            case "cpu" -> "M7 7 H17 V17 H7 Z M9 3 V7 M15 3 V7 M9 17 V21 M15 17 V21 M3 9 H7 M3 15 H7 M17 9 H21 M17 15 H21";
            case "memory" -> "M3 7 H21 V17 H3 Z M7 10 V14 M11 10 V14 M15 10 V14 M19 17 V19 M5 17 V19";
            case "database" -> "M12 3 C7 3 4 4.8 4 7 V17 C4 19.2 7 21 12 21 C17 21 20 19.2 20 17 V7 C20 4.8 17 3 12 3 M4 7 C4 9.2 7 11 12 11 C17 11 20 9.2 20 7 M4 12 C4 14.2 7 16 12 16 C17 16 20 14.2 20 12";
            default -> "M4 7 L12 3 L20 7 L12 11 Z M4 12 L12 16 L20 12 M4 17 L12 21 L20 17";
        };
        SVGPath shape=new SVGPath(); shape.setContent(path); shape.getStyleClass().add("glyph");
        shape.setStrokeLineJoin(StrokeLineJoin.ROUND); shape.setStrokeLineCap(StrokeLineCap.ROUND);
        StackPane box=new StackPane(shape); box.setMinSize(22,22); box.setPrefSize(22,22); box.setMaxSize(22,22);
        shape.setScaleX(.78); shape.setScaleY(.78); return box;
    }
}
