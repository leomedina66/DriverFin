package com.driverfin.util;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

import java.net.URL;
import java.util.Objects;

/** Gives every window and dialog the same stylesheet, and the style class names shared by several screens. */
public final class Styles {
    static final String STYLESHEET = "/com/driverfin/css/theme-dark.css";
    /** Terracotta color of a loss; the stylesheet makes it win over the normal value colors. */
    public static final String NEGATIVE_VALUE = "value-negative";

    private Styles() {}

    /** External form of the app stylesheet; fails fast when the resource is missing. */
    public static String stylesheetUrl() {
        URL url = Objects.requireNonNull(Styles.class.getResource(STYLESHEET), "Missing stylesheet " + STYLESHEET);
        return url.toExternalForm();
    }

    public static void apply(Scene scene) {
        addOnce(scene.getStylesheets());
    }

    public static void apply(DialogPane dialogPane) {
        addOnce(dialogPane.getStylesheets());
    }

    /** Adds the loss style class when negative and removes it otherwise; it is never added twice. */
    public static void markNegative(Node node, boolean negative) {
        node.getStyleClass().removeAll(NEGATIVE_VALUE);
        if (negative) node.getStyleClass().add(NEGATIVE_VALUE);
    }

    private static void addOnce(java.util.List<String> stylesheets) {
        String url = stylesheetUrl();
        if (!stylesheets.contains(url)) {
            stylesheets.add(url);
        }
    }
}
