package com.driverfin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StylesTest {
    @Test
    void stylesheetResourceExistsAndPointsToTheThemeFile() {
        String url = Styles.stylesheetUrl();
        assertTrue(url.endsWith("/com/driverfin/css/theme-dark.css"), url);
    }
}
