package io.koraframework.logging.common.masking;

import io.koraframework.logging.common.annotation.Mask;
import org.jspecify.annotations.Nullable;

public record MaskRule(String replacement, Mask.Mode mode, int keep) {

    public static MaskRule replacement(String replacement, Mask.Mode mode, int keep) {
        return new MaskRule(replacement, mode, keep);
    }

    public String apply(@Nullable Object value) {
        if (this.mode == Mask.Mode.FULL || value == null) {
            return this.replacement;
        }

        var stringValue = value.toString();
        return switch (this.mode) {
            case FULL -> this.replacement;
            case KEEP_LAST -> this.keepLast(stringValue);
            case KEEP_FIRST -> this.keepFirst(stringValue);
        };
    }

    private String keepLast(String value) {
        if (value.length() <= this.keep) {
            return this.replacement;
        }
        return this.replacement + value.substring(value.length() - this.keep);
    }

    private String keepFirst(String value) {
        if (value.length() <= this.keep) {
            return this.replacement;
        }
        return value.substring(0, this.keep) + this.replacement;
    }

}
