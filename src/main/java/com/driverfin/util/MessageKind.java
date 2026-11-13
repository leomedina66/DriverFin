package com.driverfin.util;

/** Kind of message shown under a form field, with the CSS class that styles it. */
public enum MessageKind {
    ERROR("field-error"),
    WARNING("field-warning"),
    INFO("field-info");

    private final String styleClass;

    MessageKind(String styleClass) {
        this.styleClass = styleClass;
    }

    public String styleClass() {
        return styleClass;
    }
}
