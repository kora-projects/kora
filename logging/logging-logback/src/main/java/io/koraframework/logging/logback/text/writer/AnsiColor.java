package io.koraframework.logging.logback.text.writer;

/**
 * ANSI escape codes the colored text writers highlight their parts with.
 */
public enum AnsiColor {

    CYAN("[36m"),
    BOLD_RED("[1;31m"),
    RED("[31m"),
    BLUE("[34m"),
    DEFAULT("[39m");

    private static final String RESET = "[0m";

    private final String code;

    AnsiColor(String code) {
        this.code = code;
    }

    /**
     * Appends the value wrapped into this color and a reset when {@code colored}, as is otherwise.
     */
    public void append(StringBuilder out, String value, boolean colored) {
        if (colored) {
            out.append(this.code).append(value).append(RESET);
        } else {
            out.append(value);
        }
    }
}
