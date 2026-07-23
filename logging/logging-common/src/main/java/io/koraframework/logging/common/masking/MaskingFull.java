package io.koraframework.logging.common.masking;

public final class MaskingFull implements MaskingStrategy {
    private static final String DEFAULT_REPLACEMENT = "***";

    private final String replacement;

    public MaskingFull() {
        this(DEFAULT_REPLACEMENT);
    }

    public MaskingFull(String replacement) {
        this.replacement = replacement;
    }

    @Override
    public String mask(Object value) {
        return this.replacement;
    }
}
