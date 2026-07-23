package io.koraframework.logging.common.masking;

public final class MaskingKeepFirst implements MaskingStrategy {
    private static final String DEFAULT_REPLACEMENT = "***";
    private static final int DEFAULT_KEEP = 4;

    private final String replacement;
    private final int keep;

    public MaskingKeepFirst() {
        this(DEFAULT_REPLACEMENT, DEFAULT_KEEP);
    }

    public MaskingKeepFirst(String replacement, int keep) {
        this.replacement = replacement;
        this.keep = keep;
    }

    @Override
    public String mask(Object value) {
        var stringValue = value.toString();
        if (stringValue.length() <= this.keep) {
            return this.replacement;
        }
        return stringValue.substring(0, this.keep) + this.replacement;
    }
}
