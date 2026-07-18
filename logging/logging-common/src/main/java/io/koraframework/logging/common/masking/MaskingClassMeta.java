package io.koraframework.logging.common.masking;

import org.jspecify.annotations.Nullable;

import java.util.Map;

public record MaskingClassMeta(Map<String, MaskingFieldMeta> fields) {
    @Nullable
    public MaskingFieldMeta field(String name) {
        return this.fields.get(name);
    }
}
