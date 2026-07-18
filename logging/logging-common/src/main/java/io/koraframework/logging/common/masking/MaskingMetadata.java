package io.koraframework.logging.common.masking;

import org.jspecify.annotations.Nullable;

public interface MaskingMetadata<T> {
    @Nullable
    MaskingClassMeta metadata(Class<?> type);
}
