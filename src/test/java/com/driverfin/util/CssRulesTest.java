package com.driverfin.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the stylesheet against mistakes that only show up on screen. */
class CssRulesTest {

    private static String css() throws IOException {
        try (InputStream in = CssRulesTest.class.getResourceAsStream("/com/driverfin/css/theme-dark.css")) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.replaceAll("(?s)/\\*.*?\\*/", "");
        }
    }

    /** A bare ".text" rule paints the inner Text of every Label/Button and hides each class's text color. */
    @Test
    void noRuleStylesTheInnerTextNodeOfEveryControl() throws IOException {
        Matcher m = Pattern.compile("(?m)^\\s*\\.text\\s*\\{").matcher(css());
        assertFalse(m.find(), "Do not style '.text': it overrides the -fx-text-fill of labels and buttons");
    }

    /** Colored buttons use light text. */
    @Test
    void coloredButtonsDeclareLightText() throws IOException {
        String css = css();
        for (String cls : new String[] {"btn-primary", "btn-secondary", "btn-danger"}) {
            Matcher m = Pattern.compile("(?m)^\\." + cls + "\\s*\\{([^}]*)\\}").matcher(css);
            assertTrue(m.find(), "." + cls + " rule missing");
            assertTrue(m.group(1).contains("-fx-text-fill: -c-on-dark"), "." + cls + " must set light text");
        }
    }
}
