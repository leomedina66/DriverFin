package com.driverfin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TextEditTest {

    @Test
    public void replacesAllWhenWholeTextIsSelected() {
        assertTrue(new TextEdit("150,00", 0, 6, "5").replacesAll());
        assertFalse(new TextEdit("150,00", 0, 5, "5").replacesAll());
        assertFalse(new TextEdit("", 0, 0, "5").replacesAll());
    }

    @Test
    public void deletionNeedsEmptyInsertAndARange() {
        assertTrue(new TextEdit("12", 1, 2, "").isDeletion());
        assertFalse(new TextEdit("12", 1, 1, "").isDeletion());
        assertFalse(new TextEdit("12", 1, 2, "3").isDeletion());
    }

    @Test
    public void pasteMeansMoreThanOneCharacter() {
        assertTrue(new TextEdit("", 0, 0, "830").isPaste());
        assertFalse(new TextEdit("", 0, 0, "8").isPaste());
    }

    @Test
    public void resultTextAppliesTheEdit() {
        assertEquals("aXc", new TextEdit("abc", 1, 2, "X").resultText());
    }

    @Test
    public void nullTextsBecomeEmpty() {
        TextEdit edit = new TextEdit(null, 0, 0, null);
        assertEquals("", edit.oldText());
        assertEquals("", edit.inserted());
    }

    @Test
    public void badRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TextEdit("abc", 2, 1, ""));
        assertThrows(IllegalArgumentException.class, () -> new TextEdit("abc", -1, 1, ""));
        assertThrows(IllegalArgumentException.class, () -> new TextEdit("abc", 0, 4, ""));
    }
}
