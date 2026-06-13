package com.noto.zhihui.common.constants;

public final class TodoHorizon {

    public static final String ACTION = "action";
    public static final String LONG_TERM = "long_term";

    private TodoHorizon() {
    }

    public static String normalize(String horizon) {
        if (LONG_TERM.equalsIgnoreCase(horizon)) {
            return LONG_TERM;
        }
        return ACTION;
    }
}
