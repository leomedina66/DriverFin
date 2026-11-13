package com.driverfin.util;

/** One edit of a text field: oldText[start, end) is replaced by inserted (same data as a JavaFX TextFormatter.Change). */
public record TextEdit(String oldText, int start, int end, String inserted) {

    public TextEdit {
        oldText = oldText == null ? "" : oldText;
        inserted = inserted == null ? "" : inserted;
        if (start < 0 || start > end || end > oldText.length()) {
            throw new IllegalArgumentException(
                "Invalid range [" + start + ", " + end + ") for text of length " + oldText.length());
        }
    }

    public boolean isDeletion() {
        return inserted.isEmpty() && start < end;
    }

    /** True when the whole (non-empty) text is replaced, for example select-all followed by typing. */
    public boolean replacesAll() {
        return !oldText.isEmpty() && start == 0 && end == oldText.length();
    }

    /** JavaFX does not tell paste from typing, so more than one inserted character counts as a paste. */
    public boolean isPaste() {
        return inserted.length() > 1;
    }

    /** The text JavaFX would show if there were no mask. */
    public String resultText() {
        return oldText.substring(0, start) + inserted + oldText.substring(end);
    }
}
