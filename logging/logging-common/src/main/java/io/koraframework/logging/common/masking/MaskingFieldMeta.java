package io.koraframework.logging.common.masking;

import org.jspecify.annotations.Nullable;

public record MaskingFieldMeta(Kind kind, @Nullable Class<?> type, @Nullable MaskRule rule) {
    public enum Kind {
        MASK,
        OBJECT,
        COLLECTION,
        MAP_VALUE
    }

    public static MaskingFieldMeta mask(MaskRule rule) {
        return new MaskingFieldMeta(Kind.MASK, null, rule);
    }

    public static MaskingFieldMeta object(Class<?> type) {
        return new MaskingFieldMeta(Kind.OBJECT, type, null);
    }

    public static MaskingFieldMeta collection(Class<?> type) {
        return new MaskingFieldMeta(Kind.COLLECTION, type, null);
    }

    public static MaskingFieldMeta mapValue(Class<?> type) {
        return new MaskingFieldMeta(Kind.MAP_VALUE, type, null);
    }
}
