package com.driverfin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class FieldMessageTest {

    private final FieldMessage error = FieldMessage.error("e");
    private final FieldMessage warning = FieldMessage.warning("w");
    private final FieldMessage info = FieldMessage.info("i");

    @Test
    public void strongestPrefersErrorThenWarningThenInfo() {
        assertSame(error, FieldMessage.strongest(info, error));
        assertSame(error, FieldMessage.strongest(error, warning));
        assertSame(warning, FieldMessage.strongest(info, warning));
        assertSame(warning, FieldMessage.strongest(warning, info));
    }

    @Test
    public void strongestKeepsFirstWhenKindsAreEqual() {
        FieldMessage other = FieldMessage.warning("other");
        assertSame(warning, FieldMessage.strongest(warning, other));
    }

    @Test
    public void strongestIsNullSafe() {
        assertSame(warning, FieldMessage.strongest(null, warning));
        assertSame(warning, FieldMessage.strongest(warning, null));
        assertNull(FieldMessage.strongest(null, null));
    }

    @Test
    public void kindsHaveStyleClasses() {
        assertEquals("field-error", MessageKind.ERROR.styleClass());
        assertEquals("field-warning", MessageKind.WARNING.styleClass());
        assertEquals("field-info", MessageKind.INFO.styleClass());
    }
}
