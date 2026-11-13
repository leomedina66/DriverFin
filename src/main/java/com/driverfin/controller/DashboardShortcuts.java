package com.driverfin.controller;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.function.Consumer;

/**
 * Window-wide keys of the dashboard: Ctrl+N new shift, Ctrl+B backup, Ctrl+P PDF, Ctrl+F search.
 * Only "Ctrl + letter" counts, so typing the plain letters n, b, p or f in a text field is never affected.
 */
final class DashboardShortcuts {

    private DashboardShortcuts() {}

    /**
     * Installs the shortcuts on the Scene of the node. When the node has no Scene yet, they are installed once,
     * the moment the Scene appears.
     */
    static void install(Node anyNodeInScene, Runnable newShift, Runnable backup, Runnable pdf, Runnable focusSearch) {
        whenSceneAvailable(anyNodeInScene, scene -> scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isConsumed() || !isPlainCtrl(event)) return;
            Runnable action = actionFor(event.getCode(), newShift, backup, pdf, focusSearch);
            if (action == null) return;
            event.consume();
            action.run();
        }));
    }

    /** Runs the action with the node's Scene: now when it already has one, otherwise once, when it gets one. */
    static void whenSceneAvailable(Node node, Consumer<Scene> action) {
        if (node.getScene() != null) {
            action.accept(node.getScene());
            return;
        }
        node.sceneProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Scene> observable, Scene old, Scene scene) {
                if (scene == null) return;
                observable.removeListener(this);
                action.accept(scene);
            }
        });
    }

    private static Runnable actionFor(KeyCode code, Runnable newShift, Runnable backup, Runnable pdf,
                                      Runnable focusSearch) {
        return switch (code) {
            case N -> newShift;
            case B -> backup;
            case P -> pdf;
            case F -> focusSearch;
            default -> null;
        };
    }

    /** Ctrl alone. Ctrl+Alt is how AltGr is reported on Windows, so it is not a shortcut. */
    private static boolean isPlainCtrl(KeyEvent event) {
        return event.isControlDown() && !event.isAltDown() && !event.isShiftDown() && !event.isMetaDown();
    }
}
