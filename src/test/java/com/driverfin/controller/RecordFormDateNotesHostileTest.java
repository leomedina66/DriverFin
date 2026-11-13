package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import com.driverfin.util.FormMessages;
import com.driverfin.util.FxTestSupport;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two fields without a mask (date and notes) and the end-of-field keys, with hostile content. */
class RecordFormDateNotesHostileTest {
    @TempDir
    File tempDir;

    private WorkShiftDAO dao;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void createDatabase() {
        DatabaseConnection db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, "dates.db").getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        dao = new WorkShiftDAO(db);
    }

    private final class Form extends RecordFormFixture {
        Form() {
            super(dao, null);
            root.applyCss();
            root.layout();
            typeValidShift();
        }

        void typeDate(String text) {
            TextField editor = datePicker().getEditor();
            RecordFormJourneyTest.Keys.setFocused(editor, true);   // the date text is read on save only while it has the focus
            editor.setText(text);
        }

        void ctrlEnter() {
            RecordFormJourneyTest.Keys.key(field("timeStartField"), KeyCode.ENTER, true);
        }
    }

    private List<WorkShift> rows() {
        try {
            return dao.findAll();
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void shouldRefuseEveryUnreadableOrImpossibleDateWithTheDateMessageAndWriteNothing() {
        FxTestSupport.onFxThread(() -> {
            for (String junk : List.of("31/02/2026", "29/02/2027", "00/01/2026", "32/01/2026", "01/13/2026", "1/1/26",
                    "٣٠/١١/٢٠٢٦", "３０/１１/２０２６", "30-11-2026", "2026-11-30", "30.11.2026", "abc", "😀", "01/01/10000",
                    "1".repeat(5000), "30/11/2026 25:00", "30 / 11 / 2026", "-1/1/2026")) {
                Form form = new Form();
                form.typeDate(junk);
                form.ctrlEnter();
                assertTrue(rows().isEmpty(), "\"" + abbreviate(junk) + "\" must not be saved");
                assertTrue(form.label("dateError").isVisible(), "\"" + abbreviate(junk) + "\" needs the date message");
                assertTrue(form.label("dateError").getText().contains(FormMessages.DATE_REQUIRED));
                assertSame(form.datePicker(), form.scene.getFocusOwner(), "focus goes to the date");
            }
        });
    }

    @Test
    void shouldSaveDatesThatAreOddButReal() {
        FxTestSupport.onFxThread(() -> {
            String[][] cases = {{"9/11/2026", "2026-11-09"}, {"09/11/2026", "2026-11-09"}, {"29/02/2024", "2024-02-29"},
                {"01/01/2000", "2000-01-01"}, {" 5/6/2026 ", "2026-06-05"}};
            for (String[] c : cases) {
                Form form = new Form();
                form.typeDate(c[0]);
                form.ctrlEnter();
                LocalDate expected = LocalDate.parse(c[1]);
                assertTrue(rows().stream().anyMatch(s -> s.getDate().equals(expected)),
                        "typed \"" + c[0] + "\" should be stored as " + c[1] + ", rows: " + rows().stream().map(s -> s.getDate()).toList());
            }
        });
    }

    /** The validator range (01/01/2000 to one year ahead) replaced the old "any date the calendar can hold". */
    @Test
    void shouldRefuseDatesOutsideTheAllowedRangeAndKeepTheFormOpen() {
        FxTestSupport.onFxThread(() -> {
            for (String typed : new String[]{"31/12/9999", "01/01/0001", "31/12/1999", "+10000", "-0005", "0000"}) {
                Form form = new Form();
                form.typeDate(typed);
                form.ctrlEnter();
                assertTrue(rows().isEmpty(), "typed \"" + typed + "\" must not be stored");
            }
        });
    }

    @Test
    void shouldMoveOnWithEnterWhenTheDateTextIsGarbageWithoutAnyException() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            form.typeDate("not a date");
            RecordFormJourneyTest.Keys.key(form.datePicker().getEditor(), KeyCode.ENTER);
            assertSame(form.field("timeStartField"), form.scene.getFocusOwner());
            assertEquals(null, form.datePicker().getValue());
        });
    }

    // ---- notes ----------------------------------------------------------------------------------------------------

    @Test
    void shouldStoreAndReturnNotesExactlyAsTyped() {
        FxTestSupport.onFxThread(() -> {
            List<String> notes = List.of(
                "Trânsito intenso; abastecimento R$ 150,00 — \"posto 24h\" 'ok'",
                "line1\nline2\n\nline4\tTab",
                "'); DROP TABLE work_shifts; --",
                "😀🚗💸 ‮abc‬ مرحبا שלום",
                "zero​width nbsp﻿bom",
                "%s %d ${x} {{y}} <b>bold</b> \\u0041",
                "   leading and trailing   ",
                "ç".repeat(500));
            for (String text : notes) {
                Form form = new Form();
                form.notes().setText(text);
                form.ctrlEnter();
                WorkShift stored = rows().stream().filter(s -> s.getId().equals(form.controller.getSavedShift().orElseThrow().getId()))
                        .findFirst().orElseThrow();
                assertEquals(text, stored.getNotes(), "notes must come back exactly as typed");
            }
            assertEquals(notes.size(), rows().size(), "no row was lost to the odd text");
        });
    }

    @Test
    void shouldKeepNotesWithANulCharacterWithoutLosingTheWholeShift() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            form.notes().setText("before\u0000after");
            form.ctrlEnter();
            assertTrue(form.controller.isSaved(), "a NUL in the notes must not break saving");
            assertEquals(1, rows().size());
        });
    }

    @Test
    void shouldCountEmojiInTheNotesLimitTheWayTheMessageSays() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            // 251 emoji are 502 UTF-16 characters: the user sees 251 symbols but the limit message talks about characters
            form.notes().setText("😀".repeat(251));
            form.ctrlEnter();
            assertFalse(form.controller.isSaved());
            assertTrue(form.label("notesError").getText().contains(FormMessages.notesTooLong(2)),
                    form.label("notesError").getText());
            form.notes().setText("😀".repeat(250));
            form.ctrlEnter();
            assertTrue(form.controller.isSaved(), "250 emoji = 500 characters is inside the limit");
        });
    }

    // ---- more key behaviours -----------------------------------------------------------------------------------------

    @Test
    void shouldZeroTheMoneyWithSelectAllAndBackspaceFromRealKeys() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            TextField money = form.field("earnUberField");
            RecordFormJourneyTest.Keys.focus(form, money);
            RecordFormJourneyTest.Keys.typeText(money, "123456");
            assertEquals("1.234,56", money.getText());
            RecordFormJourneyTest.Keys.key(money, KeyCode.A, true);
            RecordFormJourneyTest.Keys.key(money, KeyCode.BACK_SPACE);
            assertEquals("0,00", money.getText());
        });
    }

    @Test
    void shouldIgnoreADigitTypedAtTheCeilingAndShowTheMaximumMessage() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            TextField money = form.field("earn99Field");
            RecordFormJourneyTest.Keys.focus(form, money);
            RecordFormJourneyTest.Keys.typeText(money, "99999999");
            assertEquals("999.999,99", money.getText());
            RecordFormJourneyTest.Keys.typeChar(money, '1');
            assertEquals("999.999,99", money.getText());
            assertTrue(form.label("earn99Msg").getText().contains(FormMessages.MONEY_MAX));
        });
    }

    @Test
    void shouldSaveWithAltSOnlyOnceEvenWhenPressedTwice() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            form.press(form.field("timeStartField"), KeyCode.S, false, true);
            form.press(form.field("timeStartField"), KeyCode.S, false, true);
            assertEquals(1, rows().size(), "the second Alt+S wrote the same new shift again");
        });
    }

    private static String abbreviate(String text) {
        return text.length() > 40 ? text.substring(0, 40) + "..." : text;
    }
}
