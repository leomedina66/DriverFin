package com.driverfin.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.TextField;

/**
 * Starts the JavaFX toolkit once (no window is opened) and runs test code on the JavaFX Application Thread.
 * Tests that need it call {@link #assumeToolkit()} so they are skipped, not failed, when the toolkit cannot start.
 */
public final class FxTestSupport {
    private static final int TIMEOUT_SECONDS = 10;
    private static Boolean available;

    private FxTestSupport() {}

    static synchronized boolean toolkitAvailable() {
        if (available == null) available = startToolkit();
        return available;
    }

    public static void assumeToolkit() {
        org.junit.jupiter.api.Assumptions.assumeTrue(toolkitAvailable(), "JavaFX toolkit could not start");
    }

    private static boolean startToolkit() {
        CountDownLatch started = new CountDownLatch(1);
        try {
            // A test that shows and hides a window must not shut the toolkit down for the tests that follow.
            Platform.setImplicitExit(false);
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            return true;
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
        try {
            return started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Runs the code on the JavaFX thread and waits; an assertion failure inside is rethrown here. */
    public static void onFxThread(Runnable code) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                code.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) throw new AssertionError("JavaFX thread timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted", e);
        }
        Throwable t = failure.get();
        if (t instanceof RuntimeException) throw (RuntimeException) t;
        if (t instanceof Error) throw (Error) t;
        if (t != null) throw new AssertionError(t);
    }

    /** Simulates one key typed or a paste: the text goes through the TextFormatter like real input. */
    public static void typeAtEnd(TextField field, String text) {
        field.replaceText(field.getLength(), field.getLength(), text);
    }

    public static void type(TextField field, String keys) {
        for (char key : keys.toCharArray()) typeAtEnd(field, String.valueOf(key));
    }

    public static void backspace(TextField field) {
        if (field.getLength() > 0) field.replaceText(field.getLength() - 1, field.getLength(), "");
    }

    /** TextField whose focus can be switched without a window (Node.setFocused is protected). */
    static final class TestField extends TextField {
        void setFocusedForTest(boolean focused) {
            setFocused(focused);
        }
    }
}
