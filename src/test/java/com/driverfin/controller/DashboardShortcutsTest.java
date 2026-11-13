package com.driverfin.controller;

import com.driverfin.util.FxTestSupport;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Window-wide keys, tested with synthetic key events on a Scene that is never shown. */
class DashboardShortcutsTest {
    private int newShift;
    private int backup;
    private int pdf;
    private int focusSearch;
    private final TextField field = new TextField();
    private boolean reachedTheScene;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    private Scene sceneWithShortcuts() {
        VBox root = new VBox(field);
        DashboardShortcuts.install(field, () -> newShift++, () -> backup++, () -> pdf++, () -> focusSearch++);
        Scene scene = new Scene(root);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> reachedTheScene = true);
        return scene;
    }

    private static KeyEvent key(KeyCode code, boolean ctrl, boolean alt) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, ctrl, alt, false);
    }

    @Test
    void controlLettersTriggerTheirAction() {
        FxTestSupport.onFxThread(() -> {
            sceneWithShortcuts();
            Event.fireEvent(field, key(KeyCode.N, true, false));
            Event.fireEvent(field, key(KeyCode.B, true, false));
            Event.fireEvent(field, key(KeyCode.P, true, false));
            Event.fireEvent(field, key(KeyCode.F, true, false));
            assertEquals(1, newShift);
            assertEquals(1, backup);
            assertEquals(1, pdf);
            assertEquals(1, focusSearch);
        });
    }

    @Test
    void plainLettersTypedInATextFieldAreNotShortcuts() {
        FxTestSupport.onFxThread(() -> {
            sceneWithShortcuts();
            for (KeyCode code : new KeyCode[]{KeyCode.N, KeyCode.B, KeyCode.P, KeyCode.F}) {
                reachedTheScene = false;
                Event.fireEvent(field, key(code, false, false));
                assertTrue(reachedTheScene, "a plain letter must not be consumed");
            }
            Event.fireEvent(field, new KeyEvent(KeyEvent.KEY_TYPED, "n", "n", KeyCode.UNDEFINED, false, false, false, false));
            assertEquals(0, newShift + backup + pdf + focusSearch);
        });
    }

    @Test
    void controlAltLetterIsAltGrAndNotAShortcut() {
        FxTestSupport.onFxThread(() -> {
            sceneWithShortcuts();
            Event.fireEvent(field, key(KeyCode.N, true, true));
            assertEquals(0, newShift);
        });
    }

    @Test
    void installsTheShortcutsWhenTheSceneAppearsLater() {
        FxTestSupport.onFxThread(() -> {
            VBox root = new VBox(field);
            DashboardShortcuts.install(field, () -> newShift++, () -> backup++, () -> pdf++, () -> focusSearch++);
            new Scene(root);
            Event.fireEvent(field, key(KeyCode.N, true, false));
            assertEquals(1, newShift);
        });
    }
}
