package com.driverfin.util;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;

/** JavaFX helper: what a text field selects when it receives focus, and keeping a focused field in view. */
public final class FieldFocus {
    /** Free space kept between the revealed node and the edge of the viewport, in pixels. */
    static final double REVEAL_MARGIN = 16;

    private static final double SCROLL_MIN = 0;
    private static final double SCROLL_MAX = 1;

    private FieldFocus() {}

    /**
     * On focus gained, selects all the text (after the click is processed, so a mouse click does not undo it).
     * If the text equals caretAtEndText, the caret goes to the end instead. null = always select all.
     */
    public static void selectAllOnFocus(TextInputControl field, String caretAtEndText) {
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) Platform.runLater(() -> applySelection(field, caretAtEndText));
        });
    }

    private static void applySelection(TextInputControl field, String caretAtEndText) {
        if (caretAtEndText != null && caretAtEndText.equals(field.getText())) {
            field.positionCaret(field.getLength());
        } else {
            field.selectAll();
        }
    }

    /**
     * Scrolls the pane vertically, only as far as needed, so that {@code node} (with a small margin) is inside the
     * viewport. Focus given by code does not scroll a ScrollPane by itself: only the keyboard navigation does.
     * A node taller than the viewport is aligned to the top. Does nothing when everything already fits, when the
     * pane has no size yet, or when the node is not inside the pane's content.
     */
    public static void revealInScrollPane(ScrollPane scrollPane, Node node) {
        Node content = scrollPane.getContent();
        if (content == null || node == null || !isInside(node, content)) return;
        // The node may have just been shown or resized (a message under a field): measure after the layout.
        if (content instanceof Parent parent) {
            parent.applyCss();
            parent.layout();
        }
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        double scrollableHeight = content.getLayoutBounds().getHeight() - viewportHeight;
        if (viewportHeight <= 0 || scrollableHeight <= 0) return;
        Bounds inContent = content.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
        if (inContent == null) return;

        double viewTop = scrollPane.getVvalue() * scrollableHeight;
        double wantedTop = firstVisibleY(viewTop, viewportHeight, inContent.getMinY(), inContent.getMaxY());
        double clamped = Math.max(SCROLL_MIN, Math.min(scrollableHeight, wantedTop));
        scrollPane.setVvalue(clamped / scrollableHeight * (SCROLL_MAX - SCROLL_MIN) + SCROLL_MIN);
    }

    /** The y of the content that must be at the top of the viewport so that [nodeTop, nodeBottom] is visible. */
    private static double firstVisibleY(double viewTop, double viewportHeight, double nodeTop, double nodeBottom) {
        boolean tallerThanViewport = nodeBottom - nodeTop + 2 * REVEAL_MARGIN > viewportHeight;
        if (tallerThanViewport || nodeTop - REVEAL_MARGIN < viewTop) return nodeTop - REVEAL_MARGIN;
        if (nodeBottom + REVEAL_MARGIN > viewTop + viewportHeight) return nodeBottom + REVEAL_MARGIN - viewportHeight;
        return viewTop;
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) return true;
        }
        return false;
    }
}
