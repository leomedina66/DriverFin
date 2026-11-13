package com.driverfin.util;

import com.driverfin.model.WorkShift;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic attacks on the three masks: giant and odd pastes, random edits, exhaustive typing, round trips. */
class MasksHostileEdgeTest {
    private static final Duration PASTE_BUDGET = Duration.ofSeconds(3);
    private static final Pattern MONEY_DISPLAY = Pattern.compile("\\d{1,3}(\\.\\d{3})*,\\d{2}");

    // ---- giant pastes: nothing may throw (not even a StackOverflowError) or freeze the screen ----------------------

    private static String groups(int count) {
        return "1" + ".000".repeat(count);
    }

    @Test
    void shouldNotOverflowTheStackNorFreezeWhenPastingHugeThousandsGroupsIntoMoney() {
        assertTimeoutPreemptively(PASTE_BUDGET, () -> assertDoesNotThrow(() -> {
            MaskResult result = MoneyMask.paste("0,00", groups(50_000));
            assertEquals("0,00", result.text());
        }));
    }

    @Test
    void shouldNotOverflowTheStackNorFreezeWhenPastingHugeThousandsGroupsIntoKm() {
        assertTimeoutPreemptively(PASTE_BUDGET, () -> assertDoesNotThrow(() -> {
            MaskResult result = KmMask.apply(new TextEdit("1234", 0, 4, groups(50_000)));
            assertEquals("1234", result.text());
        }));
    }

    @Test
    void shouldNotOverflowTheStackPastingHugeThousandsGroupsWithACommaIntoMoney() {
        assertTimeoutPreemptively(PASTE_BUDGET, () -> assertDoesNotThrow(() -> {
            MaskResult result = MoneyMask.paste("0,00", groups(50_000) + ",50");
            assertEquals("0,00", result.text());
        }));
    }

    @Test
    void shouldAnswerWithinThreeSecondsToAMillionDigitsPastedIntoMoney() {
        assertTimeoutPreemptively(PASTE_BUDGET, () -> {
            MaskResult result = MoneyMask.paste("0,00", "1".repeat(1_000_000));
            assertEquals("0,00", result.text());
            assertEquals(FormMessages.MONEY_MAX, result.message().text());
        });
    }

    @Test
    void shouldAnswerWithinThreeSecondsToAHundredThousandDigitsWithACommaPastedIntoMoney() {
        assertTimeoutPreemptively(PASTE_BUDGET, () -> {
            MaskResult result = MoneyMask.paste("0,00", "1".repeat(100_000) + ",50");
            assertEquals("0,00", result.text());
        });
    }

    @Test
    void shouldAnswerQuicklyToAMillionDecimalDigitsPastedIntoMoneyAndKm() {
        assertTimeoutPreemptively(PASTE_BUDGET, () -> {
            assertEquals("0,00", MoneyMask.paste("0,00", "1," + "5".repeat(1_000_000)).text());
            assertEquals("7", KmMask.apply(new TextEdit("7", 0, 1, "1," + "5".repeat(1_000_000))).text());
        });
    }

    @Test
    void shouldAnswerQuicklyToAMillionDigitsPastedIntoKmAndTime() {
        assertTimeoutPreemptively(PASTE_BUDGET, () -> {
            assertEquals("7", KmMask.apply(new TextEdit("7", 0, 1, "1".repeat(1_000_000))).text());
            assertEquals("11:11", TimeMask.apply(new TextEdit("", 0, 0, "1".repeat(1_000_000))).text());
            assertEquals("", TimeMask.apply(new TextEdit("", 0, 0, ":".repeat(1_000_000))).text());
            assertEquals("", TimeMask.apply(new TextEdit("", 0, 0, " ".repeat(1_000_000))).text());
        });
    }

    @Test
    void shouldSurviveAMillionWhitespaceAndCurrencySignsPasted() {
        assertTimeoutPreemptively(PASTE_BUDGET, () -> {
            assertEquals("5,00", MoneyMask.paste("0,00", "R$ ".repeat(100_000) + "5").text());
            assertEquals("0,00", MoneyMask.paste("0,00", " ".repeat(1_000_000)).text());
        });
    }

    // ---- unreadable pastes always explain themselves ------------------------------------------------------------

    @Test
    void shouldExplainEveryMoneyPasteThatIsNotAMoneyValue() {
        for (String junk : List.of("٣٠٠", "１５０", "1e9", "1E9", "NaN", "Infinity", "-5", "+5", "abc", "5,5,5", "1.2.3",
                ",", ".", ",50", "1,234", "1,2345", "R$ -5", "5-", "0x1F", "1 2 3 abc", "​", "😀", "٫٥")) {
            MaskResult result = MoneyMask.paste("1,23", junk);
            assertEquals("1,23", result.text(), "\"" + junk + "\" must leave the field alone");
            assertTrue(result.message() != null && !result.message().text().isBlank(),
                    "\"" + junk + "\" must show a message");
        }
    }

    @Test
    void shouldReadTheReaisPastesTheUxDocumentPromises() {
        assertEquals("1.234,56", MoneyMask.paste("0,00", "R$ 1.234,56").text());
        assertEquals("1.234,56", MoneyMask.paste("0,00", "1234,56").text());
        assertEquals("1.234,56", MoneyMask.paste("0,00", "1.234,56").text());
        assertEquals("123.456,00", MoneyMask.paste("0,00", "123456").text());
        assertEquals("150,50", MoneyMask.paste("0,00", "150,5").text());
        assertEquals("1.234,50", MoneyMask.paste("0,00", "1234.5").text());
        assertEquals("1.234,00", MoneyMask.paste("0,00", "1.234").text());
        assertEquals("0,00", MoneyMask.paste("0,00", "1.234.567").text(), "over the ceiling");
        assertEquals("1.000.000,00", MoneyMask.paste("0,00", "1000000").text());
        assertEquals("0,00", MoneyMask.paste("0,00", "1000001").text());
        assertEquals("0,00", MoneyMask.paste("0,00", "1000000,01").text());
    }

    // ---- exhaustive: every digit sequence typed into the time mask --------------------------------------------

    @Test
    void shouldTurnEveryFourDigitSequenceTypedIntoAShapeTheValidatorUnderstandsOnExit() {
        for (int n = 0; n < 10_000; n++) {
            String digits = String.format("%04d", n);
            String text = "";
            for (char digit : digits.toCharArray()) {
                text = TimeMask.apply(new TextEdit(text, text.length(), text.length(), String.valueOf(digit))).text();
            }
            MaskResult exit = TimeMask.complete(text);
            boolean validTime = Integer.parseInt(digits.substring(0, 2)) <= 23
                    && Integer.parseInt(digits.substring(2)) <= 59;
            if (validTime) {
                assertEquals(digits.substring(0, 2) + ":" + digits.substring(2), text, "typing " + digits);
            }
            if (exit.message() == null) {
                assertTrue(TimeMask.isComplete(exit.text()), "typing " + digits + " left \"" + text + "\" -> \"" + exit.text() + "\"");
            } else {
                assertEquals(FormMessages.TIME_INCOMPLETE, exit.message().text(), "typing " + digits + " left \"" + text + "\"");
            }
        }
    }

    @Test
    void shouldKeepTheTimeTextACompletableStateAfterAnySingleKeyInAnyState() {
        List<String> states = List.of("", "0", "1", "2", "08:", "23:", "08:3", "08:30", "23:59", "20:", "00:00");
        String keys = "0123456789:,.- aA٣３😀​ \t";
        for (String state : states) {
            for (char key : keys.toCharArray()) {
                for (int start = 0; start <= state.length(); start++) {
                    for (int end = start; end <= state.length(); end++) {
                        MaskResult result = TimeMask.apply(new TextEdit(state, start, end, String.valueOf(key)));
                        assertTrue(Pattern.matches("|[0-2]|([01]\\d|2[0-3]):([0-5]\\d?)?", result.text()),
                                "state \"" + state + "\" [" + start + "," + end + ") key " + (int) key + " -> \"" + result.text() + "\"");
                    }
                }
            }
        }
    }

    // ---- money: random edits keep the canonical form ------------------------------------------------------------

    @Test
    void shouldKeepMoneyCanonicalAndUnderTheCeilingAfterRandomEdits() {
        String alphabet = "0123456789,.-R$ abc٣３😀​ e+";
        String[] chunks = {"", "5", "12", "1234", "R$ 12,50", "1.234,56", "999999999", "-1", "1e9", "٣٠٠", "0", "00"};
        for (long seed = 1; seed <= 200; seed++) {
            Random random = new Random(seed);
            String text = "0,00";
            for (int step = 0; step < 300; step++) {
                int start = random.nextInt(text.length() + 1);
                int end = start + random.nextInt(text.length() - start + 1);
                String inserted = random.nextInt(3) == 0 ? chunks[random.nextInt(chunks.length)]
                        : String.valueOf(alphabet.charAt(random.nextInt(alphabet.length())));
                MaskResult result = MoneyMask.apply(new TextEdit(text, start, end, inserted));
                text = result.text();
                assertTrue(MONEY_DISPLAY.matcher(text).matches(), "seed " + seed + " step " + step + " gave \"" + text + "\"");
                assertTrue(MoneyMask.parseDisplay(text) <= MoneyMask.MAX_CENTS, "seed " + seed + " step " + step);
                assertEquals(MoneyMask.display(MoneyMask.parseDisplay(text)), text, "canonical, seed " + seed);
                assertTrue(result.caret() >= 0 && result.caret() <= text.length(), "caret inside the text");
            }
        }
    }

    @Test
    void shouldGiveTheCentsOfTheTypedDigitsWhateverTheLeadingZerosAndLength() {
        Random random = new Random(7);
        for (int run = 0; run < 3000; run++) {
            int length = 1 + random.nextInt(14);
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < length; i++) digits.append((char) ('0' + random.nextInt(10)));
            String text = MoneyMask.ZERO;
            BigInteger expected = BigInteger.ZERO;
            for (char digit : digits.toString().toCharArray()) {
                BigInteger next = expected.multiply(BigInteger.TEN).add(BigInteger.valueOf(digit - '0'));
                text = MoneyMask.apply(new TextEdit(text, text.length(), text.length(), String.valueOf(digit))).text();
                if (next.compareTo(BigInteger.valueOf(MoneyMask.MAX_CENTS)) <= 0) expected = next;
                assertEquals(MoneyMask.display(expected.longValueExact()), text, "typing " + digits);
            }
        }
    }

    @Test
    void shouldGoBackOneDigitAtATimeToZeroWithBackspace() {
        String text = "1.234.567,89";
        StringBuilder trail = new StringBuilder(text);
        for (int i = 0; i < 12; i++) {
            text = MoneyMask.apply(new TextEdit(text, text.length() - 1, text.length(), "")).text();
            trail.append(" > ").append(text);
            assertTrue(MONEY_DISPLAY.matcher(text).matches(), trail.toString());
        }
        assertEquals("0,00", text, trail.toString());
    }

    // ---- money: what the form shows is what is stored and what the validator reads --------------------------------

    @Test
    void shouldRoundTripEveryCentValueBetweenFieldTextPasteValidatorAndStorage() {
        Random random = new Random(11);
        for (long cents : sampleCents(random)) {
            String shown = MoneyMask.display(cents);
            assertEquals(cents, MoneyMask.parseDisplay(shown), shown);
            // the edit form fills the field with setText, which goes through the paste rule
            assertEquals(shown, MoneyMask.paste("0,00", shown).text(), "refilling the field with " + shown);
            BigDecimal reais = MoneyMask.toReais(cents);
            assertEquals(cents, MoneyMask.toCents(reais), shown);
            // the validator reads the plain text
            WorkShift shift = new WorkShift();
            String plain = MoneyMask.displayToPlain(shown);
            WorkShiftValidator.validateAndPopulate(shift, LocalDate.of(2026, 1, 1), "08:00", "09:00", "0", "1",
                    plain, "0,00", "0,00", "0,00", "0,00", "");
            assertEquals(0, reais.compareTo(shift.getUberEarnings()), shown + " -> " + plain);
            assertEquals(cents, MoneyMask.toCents(shift.getUberEarnings()), shown);
            // and the screen format shows the same digits as the field does
            String screen = CurrencyFormat.money(reais).replace(' ', ' ');
            assertEquals("R$ " + shown, screen, "form text vs CurrencyFormat for " + cents);
        }
    }

    private static long[] sampleCents(Random random) {
        long[] fixed = {0, 1, 5, 9, 10, 99, 100, 101, 1999, 12345, 99999, 100000, 123456, 99999999, 100000000};
        long[] all = new long[fixed.length + 5000];
        System.arraycopy(fixed, 0, all, 0, fixed.length);
        for (int i = fixed.length; i < all.length; i++) {
            all[i] = (long) (random.nextDouble() * (MoneyMask.MAX_CENTS + 1));
        }
        return all;
    }

    // ---- km -------------------------------------------------------------------------------------------------------

    @Test
    void shouldKeepKmInTypingShapeAfterRandomEditsAndNeverThrow() {
        String alphabet = "0123456789,.-e R$aN٣３😀​";
        String[] chunks = {"", "5", "12450", "12.450,5", "12450.5", "-5", "1e9", "NaN", "1,23", "100,555", "٣٠٠", "9999999999"};
        Pattern shape = Pattern.compile("\\d{0,9}(,\\d{0,3})?");
        for (long seed = 1; seed <= 200; seed++) {
            Random random = new Random(seed);
            String text = "";
            for (int step = 0; step < 300; step++) {
                int start = random.nextInt(text.length() + 1);
                int end = start + random.nextInt(text.length() - start + 1);
                String inserted = random.nextInt(4) == 0 ? chunks[random.nextInt(chunks.length)]
                        : String.valueOf(alphabet.charAt(random.nextInt(alphabet.length())));
                MaskResult result = KmMask.apply(new TextEdit(text, start, end, inserted));
                text = result.text();
                assertTrue(shape.matcher(text.replace(".", "")).matches(),
                        "seed " + seed + " step " + step + " gave \"" + text + "\"");
                assertTrue(result.caret() >= 0 && result.caret() <= text.length(), "caret inside the text");
                MaskResult exit = KmMask.formatOnExit(text);
                if (!text.isBlank() && exit.message() == null) {
                    assertEquals(exit.text(), KmMask.formatOnExit(exit.text()).text(), "exit formatting is stable");
                    // leave the field now and then, so editing also continues from the grouped form "12.450,0"
                    if (random.nextInt(6) == 0) text = exit.text();
                }
            }
        }
    }

    @Test
    void shouldRoundTripKmBetweenDisplayParseAndValidator() {
        Random random = new Random(5);
        for (int run = 0; run < 5000; run++) {
            long thousandths = (long) (random.nextDouble() * 999_999_999_999L);
            double km = thousandths / 1000.0;
            String shown = KmMask.display(km);
            assertEquals(km, KmMask.parse(shown).orElseThrow(), 1e-9, shown);
            assertEquals(shown, KmMask.formatOnExit(shown).text(), "stable on exit: " + shown);
            WorkShift shift = new WorkShift();
            WorkShiftValidator.validateAndPopulate(shift, LocalDate.of(2026, 1, 1), "08:00", "09:00", "0",
                    KmMask.toPlain(shown), "0,00", "0,00", "0,00", "0,00", "0,00", "");
            assertEquals(km, shift.getKmEnd(), 1e-9, shown);
        }
    }

    // ---- the form checks must agree with the validator ---------------------------------------------------------------

    @Test
    void shouldNeverLetTheValidatorRejectWhatTheFormChecksAcceptedForTextsTheUiCanShow() {
        Random random = new Random(3);
        String timeChars = "0123456789:";
        String kmChars = "0123456789,.";
        int accepted = 0;
        for (int run = 0; run < 40_000; run++) {
            String start = randomText(random, timeChars, 0, 6);
            String end = randomText(random, timeChars, 0, 6);
            String kmStart = randomText(random, kmChars, 0, 8);
            String kmEnd = randomText(random, kmChars, 0, 8);
            String notes = random.nextInt(50) == 0 ? "n".repeat(499 + random.nextInt(4)) : "ok";
            ShiftFormInput input = new ShiftFormInput(LocalDate.of(2026, 1, 1), start, end, kmStart, kmEnd, notes);
            if (!ShiftFormCheck.checkAll(input).isEmpty()) continue;
            accepted++;
            WorkShift shift = new WorkShift();
            try {
                WorkShiftValidator.validateAndPopulate(shift, LocalDate.of(2026, 1, 1),
                        TimeMask.complete(start).text(), TimeMask.complete(end).text(),
                        KmMask.toPlain(kmStart), KmMask.toPlain(kmEnd), "0,00", "0,00", "0,00", "0,00", "0,00", notes);
            } catch (IllegalArgumentException e) {
                throw new AssertionError("form checks accepted start=\"" + start + "\" end=\"" + end + "\" kmStart=\""
                        + kmStart + "\" kmEnd=\"" + kmEnd + "\" notes=" + notes.length() + " but the validator said: "
                        + e.getMessage(), e);
            }
        }
        assertTrue(accepted > 50, "the generator must produce some accepted inputs (" + accepted + ")");
    }

    @Test
    void shouldAgreeOnTheNotesLimitOfFiveHundredCharacters() {
        for (int length : new int[]{0, 1, 499, 500, 501, 502}) {
            String notes = "n".repeat(length);
            Optional<FieldError> formSays = ShiftFormCheck.checkField(ShiftField.NOTES,
                    new ShiftFormInput(LocalDate.of(2026, 1, 1), "08:00", "09:00", "1", "2", notes));
            boolean validatorRejects;
            try {
                WorkShiftValidator.validateAndPopulate(new WorkShift(), LocalDate.of(2026, 1, 1), "08:00", "09:00",
                        "1", "2", "0,00", "0,00", "0,00", "0,00", "0,00", notes);
                validatorRejects = false;
            } catch (FieldValidationException e) {
                validatorRejects = true;
            }
            assertEquals(validatorRejects, formSays.isPresent(), "notes of " + length + " characters");
        }
    }

    private static String randomText(Random random, String alphabet, int minLength, int maxLength) {
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < length; i++) text.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return text.toString();
    }
}
