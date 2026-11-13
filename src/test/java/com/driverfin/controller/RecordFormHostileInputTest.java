package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import com.driverfin.util.FormMessages;
import com.driverfin.util.FxTestSupport;
import com.driverfin.util.KmMask;
import com.driverfin.util.MoneyMask;
import com.driverfin.util.TimeMask;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tries to break the three masked kinds of field (time, km, money) on the real form with hostile text, key storms,
 * odd selections and Unicode. Rule under test: the field text stays in a shape the validator accepts, the form never
 * throws, and when saving is refused a message sits under a field.
 */
class RecordFormHostileInputTest {
    private static final List<String> TIME_IDS = List.of("timeStartField", "timeEndField");
    private static final List<String> KM_IDS = List.of("kmStartField", "kmEndField");
    private static final List<String> MONEY_IDS =
            List.of("earnUberField", "earn99Field", "earnOthersField", "costFuelField", "costFoodOtherField");
    private static final List<String> ALL_IDS = new ArrayList<>();

    static {
        ALL_IDS.addAll(TIME_IDS);
        ALL_IDS.addAll(KM_IDS);
        ALL_IDS.addAll(MONEY_IDS);
    }

    /** Text states of the time mask: nothing, one hour digit, "HH:", "HH:m", "HH:mm". */
    private static final Pattern TIME_SHAPE = Pattern.compile("|[0-2]|([01]\\d|2[0-3]):([0-5]\\d?)?");
    private static final Pattern KM_SHAPE_WITHOUT_DOTS = Pattern.compile("\\d{0,9}(,\\d{0,3})?");

    private static final String[] HOSTILE = {
        "٣٠٠", "٠٨٠٠", "０８００", "１５０００", "😀", "😀😀😀", "\u202E123", "\u202B08:30", "1\u200B2", "\u200B", "\uFEFF08:30",
        "1\u00A02", "\u00A0", "\n", "\t", "\r\n", "1\n2", "1e9", "1E+30", "NaN", "Infinity", "-Infinity", "-5", "+5", "-0",
        "R$ R$ 5", "R$", "R$ -5", "5%", "0x10", "1_000", "1 000", "1.000.000.000", "1,2,3", ",", ".", "..", ",,", ",5",
        "5,", "1.2.3", "9".repeat(10_000), "0".repeat(10_000), "08:30:45", "24:00", "25:61", "99:99", ":", "::", "12:",
        "'; DROP TABLE work_shifts; --", "<b>5</b>", "${jndi:x}", "%s%d", "\\u0030", "1,234567", "100.000,00"
    };
    private static final String TYPE_ALPHABET = "0123456789,.:-e R$aN+٣３\u200B\u00A0😀";

    @TempDir
    File tempDir;

    private WorkShiftDAO dao;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void createDatabase() {
        DatabaseConnection db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, "hostile.db").getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        dao = new WorkShiftDAO(db);
    }

    private final class Form extends RecordFormFixture {
        Form() {
            super(dao, null);
            root.applyCss();
            root.layout();
        }
    }

    /** Collects what goes wrong silently: exceptions inside listeners and the controller's own warnings. */
    private static final class Trouble extends Handler implements Thread.UncaughtExceptionHandler {
        final List<String> found = new ArrayList<>();
        private final Logger logger = Logger.getLogger(RecordFormController.class.getName());
        private Thread.UncaughtExceptionHandler previous;

        void start() {
            logger.addHandler(this);
            previous = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler(this);
        }

        void stop() {
            logger.removeHandler(this);
            Thread.currentThread().setUncaughtExceptionHandler(previous);
        }

        @Override public void uncaughtException(Thread thread, Throwable error) {
            found.add("uncaught " + error);
        }

        @Override public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) found.add("log " + record.getMessage());
        }

        @Override public void flush() {}

        @Override public void close() {}
    }

    private static void assertShapes(Form form, String context) {
        for (String id : TIME_IDS) {
            String text = form.field(id).getText();
            assertTrue(TIME_SHAPE.matcher(text).matches(), id + " has a text the mask can never make: \"" + text + "\" after " + context);
        }
        for (String id : KM_IDS) {
            String text = form.field(id).getText();
            String withoutDots = text.replace(".", "");
            assertTrue(KM_SHAPE_WITHOUT_DOTS.matcher(withoutDots).matches(), id + " has a text the km filter can never make: \"" + text + "\" after " + context);
        }
        for (String id : MONEY_IDS) {
            String text = form.field(id).getText();
            long cents = MoneyMask.parseDisplay(text);    // throws on anything outside the display format
            assertEquals(MoneyMask.display(cents), text, id + " is not canonical after " + context);
            assertTrue(cents <= MoneyMask.MAX_CENTS, id + " passed the ceiling after " + context);
        }
    }

    private List<WorkShift> rows() {
        try {
            return dao.findAll();
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean anyErrorVisible(Form form) {
        for (String id : List.of("dateError", "timeStartError", "timeEndError", "kmStartError", "kmEndError", "notesError")) {
            Label label = form.label(id);
            if (label.isVisible() && !label.getText().isBlank()) return true;
        }
        return false;
    }

    // ---- explicit hostile pastes --------------------------------------------------------------------------

    @Test
    void shouldKeepEveryFieldInShapeAfterEachHostilePasteOnAFreshSelection() {
        FxTestSupport.onFxThread(() -> {
            Trouble trouble = new Trouble();
            trouble.start();
            try {
                for (String hostile : HOSTILE) {
                    Form form = new Form();
                    for (String id : ALL_IDS) {
                        TextField field = form.field(id);
                        field.selectAll();
                        field.replaceSelection(hostile);
                        assertShapes(form, "pasting \"" + abbreviate(hostile) + "\" into " + id);
                    }
                }
            } finally {
                trouble.stop();
            }
            assertEquals(List.of(), trouble.found);
        });
    }

    @Test
    void shouldKeepEveryFieldInShapeWhenHostileTextIsPastedInTheMiddleOfAFilledValue() {
        FxTestSupport.onFxThread(() -> {
            Trouble trouble = new Trouble();
            trouble.start();
            try {
                for (String hostile : HOSTILE) {
                    Form form = new Form();
                    form.typeValidShift();
                    for (String id : ALL_IDS) {
                        TextField field = form.field(id);
                        int caret = field.getLength() / 2;
                        field.positionCaret(caret);
                        field.replaceSelection(hostile);
                        assertShapes(form, "pasting \"" + abbreviate(hostile) + "\" in the middle of " + id);
                    }
                }
            } finally {
                trouble.stop();
            }
            assertEquals(List.of(), trouble.found);
        });
    }

    @Test
    void shouldTellTheUserWhenMoneyOrKmPasteIsNotANumberAndLeaveTheFieldAsItWas() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            for (String junk : List.of("٣٠٠", "１５０", "1e9", "NaN", "-5", "abc", "1,234567", "😀", "1.2.3")) {
                TextField money = form.field("earnUberField");
                money.selectAll();
                money.replaceSelection("1234");     // a known value first
                String before = money.getText();
                money.selectAll();
                money.replaceSelection(junk);
                assertEquals(before, money.getText(), "money paste \"" + junk + "\" must not change the field");
                Label message = form.label("earnUberMsg");
                assertTrue(message.isVisible() && message.getText().contains("Não entendi"),
                        "money paste \"" + junk + "\" needs the field message, got \"" + message.getText() + "\"");

                TextField km = form.field("kmStartField");
                km.selectAll();
                km.replaceSelection("1234");
                km.selectAll();
                km.replaceSelection(junk);
                assertEquals("1234", km.getText(), "km paste \"" + junk + "\" must not change the field");
                Label kmMessage = form.label("kmStartError");
                assertTrue(kmMessage.isVisible() && kmMessage.getText().contains("Não entendi"),
                        "km paste \"" + junk + "\" needs the field message, got \"" + kmMessage.getText() + "\"");
            }
        });
    }

    @Test
    void shouldIgnoreNonAsciiDigitsTypedOnTheKeyboardInEveryKindOfField() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            for (String id : ALL_IDS) {
                TextField field = form.field(id);
                String before = field.getText();
                RecordFormJourneyTest.Keys.focus(form, field);
                RecordFormJourneyTest.Keys.typeText(field, "٣٠٠０８😀\u200B\u00A0");
                assertEquals(before, field.getText(), id + " accepted a character that is not an ASCII digit");
            }
        });
    }

    @Test
    void shouldNotTreatAPastedNewlineOrTabAsInputInTimeOrMoneyFields() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            for (String id : TIME_IDS) {
                TextField field = form.field(id);
                field.replaceSelection("\n\t");
                assertEquals("", field.getText());
            }
            for (String id : MONEY_IDS) {
                TextField field = form.field(id);
                field.replaceSelection("\n\t");
                assertEquals("0,00", field.getText());
            }
        });
    }

    // ---- many keys, fast ----------------------------------------------------------------------------------

    @Test
    void shouldSurviveThousandsOfDigitsTypedFastIntoEveryField() {
        FxTestSupport.onFxThread(() -> {
            Trouble trouble = new Trouble();
            trouble.start();
            try {
                Form form = new Form();
                for (String id : ALL_IDS) {
                    TextField field = form.field(id);
                    RecordFormJourneyTest.Keys.focus(form, field);
                    for (int i = 0; i < 3000; i++) RecordFormJourneyTest.Keys.typeChar(field, (char) ('0' + i % 10));
                }
                assertShapes(form, "3000 digits in every field");
                assertEquals("01:23", form.field("timeStartField").getText(), "the time field stops at four digits");
                assertEquals("12.345.678,0", form.field("kmStartField").getText(), "km stops at nine integer digits (shown grouped after leaving it)");
            } finally {
                trouble.stop();
            }
            assertEquals(List.of(), trouble.found);
        });
    }

    @Test
    void shouldStopMoneyAtTheCeilingAndTellWhy() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            TextField field = form.field("costFuelField");
            RecordFormJourneyTest.Keys.focus(form, field);
            RecordFormJourneyTest.Keys.typeText(field, "100000000");
            assertEquals("1.000.000,00", field.getText());
            RecordFormJourneyTest.Keys.typeChar(field, '0');
            assertEquals("1.000.000,00", field.getText());
            assertTrue(form.label("costFuelMsg").getText().contains(FormMessages.MONEY_MAX));
            RecordFormJourneyTest.Keys.key(field, KeyCode.BACK_SPACE);
            assertEquals("100.000,00", field.getText());
        });
    }

    // ---- selections and deleting at every position --------------------------------------------------------

    @Test
    void shouldKeepShapesWhenBackspaceAndDeleteAreUsedAtEveryCaretPosition() {
        FxTestSupport.onFxThread(() -> {
            Trouble trouble = new Trouble();
            trouble.start();
            try {
                for (String id : ALL_IDS) {
                    for (boolean forward : new boolean[]{false, true}) {
                        Form probe = new Form();
                        probe.typeValidShift();
                        int length = probe.field(id).getLength();
                        for (int caret = 0; caret <= length; caret++) {
                            Form each = new Form();
                            each.typeValidShift();
                            TextField field = each.field(id);
                            field.positionCaret(Math.min(caret, field.getLength()));
                            if (forward) field.deleteNextChar();
                            else field.deletePreviousChar();
                            assertShapes(each, (forward ? "Delete" : "Backspace") + " at " + caret + " in " + id);
                        }
                    }
                }
            } finally {
                trouble.stop();
            }
            assertEquals(List.of(), trouble.found);
        });
    }

    @Test
    void shouldStartANewValueWhenAllTheTextIsSelectedAndADigitIsTyped() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            form.typeValidShift();
            TextField time = form.field("timeStartField");
            time.selectAll();
            RecordFormJourneyTest.Keys.typeChar(time, '9');
            assertEquals("09:", time.getText());

            TextField km = form.field("kmEndField");
            km.selectAll();
            RecordFormJourneyTest.Keys.typeChar(km, '7');
            assertEquals("7", km.getText());

            TextField money = form.field("earnUberField");
            money.selectAll();
            RecordFormJourneyTest.Keys.typeChar(money, '5');
            assertEquals("0,05", money.getText());
        });
    }

    @Test
    void shouldZeroMoneyWithDeleteKeyBackspaceOnSelectionAndCutWhateverTheCaret() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            TextField money = form.field("costFuelField");
            for (int caret = 0; caret <= 4; caret++) {
                money.selectAll();
                money.replaceSelection("12345");
                money.positionCaret(caret);
                RecordFormJourneyTest.Keys.key(money, KeyCode.DELETE);
                assertEquals("0,00", money.getText(), "Delete key with the caret at " + caret);
            }
            money.selectAll();
            money.replaceSelection("12345");
            money.selectAll();
            money.cut();
            assertEquals("0,00", money.getText(), "cut of the whole value");
        });
    }

    @Test
    void shouldNeverBreakTheShapeWithUndoRedoAndCutShortcuts() {
        FxTestSupport.onFxThread(() -> {
            Trouble trouble = new Trouble();
            trouble.start();
            try {
                Form form = new Form();
                for (String id : ALL_IDS) {
                    TextField field = form.field(id);
                    RecordFormJourneyTest.Keys.focus(form, field);
                    RecordFormJourneyTest.Keys.typeText(field, "1234");
                    for (KeyCode code : List.of(KeyCode.Z, KeyCode.Z, KeyCode.Y, KeyCode.X, KeyCode.Z, KeyCode.A, KeyCode.Y)) {
                        RecordFormJourneyTest.Keys.key(field, code, true);
                        assertShapes(form, "Ctrl+" + code + " in " + id);
                    }
                }
            } finally {
                trouble.stop();
            }
            assertEquals(List.of(), trouble.found);
        });
    }

    // ---- the big one: random storms, then save --------------------------------------------------------------

    @Test
    void shouldNeverShowATechnicalProblemAndSaveExactlyWhatTheFieldsShowAfterRandomStorms() {
        FxTestSupport.onFxThread(() -> {
            Trouble trouble = new Trouble();
            trouble.start();
            try {
                int saved = 0;
                int refused = 0;
                for (long seed = 1; seed <= 60; seed++) {
                    Random random = new Random(seed);
                    Form form = new Form();
                    form.typeValidShift();
                    int before = rows().size();
                    StringBuilder history = new StringBuilder("seed " + seed + ":");
                    for (int step = 0; step < 80; step++) {
                        history.append(' ').append(randomStep(form, random));
                        assertShapes(form, abbreviate(history.toString()));
                    }
                    if (saveAndCompare(form, before, history.toString())) saved++;
                    else refused++;
                }
                assertTrue(saved >= 3 && refused >= 3, "the storms must reach both outcomes (saved=" + saved
                        + ", refused=" + refused + "), otherwise the test proves little");
            } finally {
                trouble.stop();
            }
            assertEquals(List.of(), trouble.found, "something failed silently or the validator had to catch the form");
        });
    }

    private boolean saveAndCompare(Form form, int rowsBefore, String history) {
        RecordFormJourneyTest.Keys.key(form.field("timeStartField"), KeyCode.ENTER, true);
        List<WorkShift> after = rows();
        if (!form.controller.isSaved()) {
            assertEquals(rowsBefore, after.size(), "a refused save must not write: " + abbreviate(history));
            assertTrue(anyErrorVisible(form), "save refused but no message under any field: " + abbreviate(history));
            return false;
        }
        assertEquals(rowsBefore + 1, after.size(), abbreviate(history));
        WorkShift row = after.stream().filter(s -> s.getId().equals(form.controller.getSavedShift().orElseThrow().getId()))
                .findFirst().orElseThrow();
        assertEquals(LocalTime.parse(TimeMask.complete(form.field("timeStartField").getText()).text()), row.getTimeStart(), abbreviate(history));
        assertEquals(LocalTime.parse(TimeMask.complete(form.field("timeEndField").getText()).text()), row.getTimeEnd(), abbreviate(history));
        assertEquals(KmMask.parse(form.field("kmStartField").getText()).orElseThrow(), row.getKmStart(), 0.0001, abbreviate(history));
        assertEquals(KmMask.parse(form.field("kmEndField").getText()).orElseThrow(), row.getKmEnd(), 0.0001, abbreviate(history));
        assertEquals(MoneyMask.parseDisplay(form.field("earnUberField").getText()),
                MoneyMask.toCents(row.getUberEarnings()), "uber " + abbreviate(history));
        assertEquals(MoneyMask.parseDisplay(form.field("earn99Field").getText()),
                MoneyMask.toCents(row.getApp99Earnings()), "99 " + abbreviate(history));
        assertEquals(MoneyMask.parseDisplay(form.field("earnOthersField").getText()),
                MoneyMask.toCents(row.getOtherEarnings()), "others " + abbreviate(history));
        assertEquals(MoneyMask.parseDisplay(form.field("costFuelField").getText()),
                MoneyMask.toCents(row.getCostFuel()), "fuel " + abbreviate(history));
        assertEquals(MoneyMask.parseDisplay(form.field("costFoodOtherField").getText()),
                MoneyMask.toCents(row.getFoodAndOtherCosts()), "food " + abbreviate(history));
        return true;
    }

    /** One random user action on a random masked field. Returns a short description for the failure message. */
    private static String randomStep(Form form, Random random) {
        String id = ALL_IDS.get(random.nextInt(ALL_IDS.size()));
        TextField field = form.field(id);
        int length = field.getLength();
        switch (random.nextInt(11)) {
            case 0, 1, 2 -> {
                char c = TYPE_ALPHABET.charAt(random.nextInt(TYPE_ALPHABET.length()));
                RecordFormJourneyTest.Keys.focus(form, field);
                RecordFormJourneyTest.Keys.typeChar(field, c);
                return id + ".type(" + (int) c + ")";
            }
            case 3 -> {
                int a = random.nextInt(length + 1);
                int b = a + random.nextInt(length - a + 1);
                String text = HOSTILE[random.nextInt(HOSTILE.length)];
                field.replaceText(a, b, text);
                return id + ".replace(" + a + "," + b + ",\"" + abbreviate(text) + "\")";
            }
            case 4 -> {
                RecordFormJourneyTest.Keys.key(field, KeyCode.BACK_SPACE);
                return id + ".backspace";
            }
            case 5 -> {
                RecordFormJourneyTest.Keys.key(field, KeyCode.DELETE);
                return id + ".delete";
            }
            case 6 -> {
                field.selectAll();
                char digit = (char) ('0' + random.nextInt(10));
                RecordFormJourneyTest.Keys.typeChar(field, digit);
                return id + ".selectAllThen" + digit;
            }
            case 7 -> {
                int caret = random.nextInt(length + 1);
                field.positionCaret(caret);
                if (random.nextBoolean()) field.deletePreviousChar();
                else field.deleteNextChar();
                return id + ".deleteAt" + caret;
            }
            case 8 -> {
                RecordFormJourneyTest.Keys.setFocused(field, true);
                RecordFormJourneyTest.Keys.setFocused(field, false);
                return id + ".leave";
            }
            case 9 -> {
                KeyCode code = List.of(KeyCode.Z, KeyCode.Y, KeyCode.X, KeyCode.A).get(random.nextInt(4));
                RecordFormJourneyTest.Keys.key(field, code, true);
                return id + ".ctrl" + code;
            }
            default -> {
                int a = random.nextInt(length + 1);
                field.selectRange(a, a + random.nextInt(length - a + 1));
                field.replaceSelection(String.valueOf(random.nextInt(1000)));
                return id + ".pasteNumberOverSelection";
            }
        }
    }

    private static String abbreviate(String text) {
        String flat = text.replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r");
        return flat.length() > 300 ? flat.substring(flat.length() - 300) : flat;
    }

    @Test
    void shouldNotLeaveAnErrorMessageOnTheScreenWhenTheFieldIsBackToAValidValue() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form();
            TextField time = form.field("timeEndField");
            RecordFormJourneyTest.Keys.focus(form, time);
            RecordFormJourneyTest.Keys.typeText(time, "173");
            RecordFormJourneyTest.Keys.setFocused(time, false);      // leave with half-written minutes
            assertTrue(form.label("timeEndError").isVisible());
            RecordFormJourneyTest.Keys.focus(form, time);
            RecordFormJourneyTest.Keys.key(time, KeyCode.BACK_SPACE);   // "17:"
            RecordFormJourneyTest.Keys.key(time, KeyCode.BACK_SPACE);   // "1"
            RecordFormJourneyTest.Keys.key(time, KeyCode.BACK_SPACE);   // ""
            assertEquals("", time.getText());
            assertFalse(anyErrorVisible(form) && form.label("timeEndError").getText().contains(FormMessages.TIME_INCOMPLETE),
                    "an empty untouched-looking field must not keep the old 'complete the minutes' message");
        });
    }
}
