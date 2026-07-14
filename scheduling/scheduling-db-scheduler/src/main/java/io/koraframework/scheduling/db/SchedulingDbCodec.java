package io.koraframework.scheduling.db;

import io.koraframework.application.graph.TypeRef;
import org.jspecify.annotations.Nullable;

public interface SchedulingDbCodec<T> {

    TypeRef<T> typeRef();

    byte[] serialize(@Nullable T value);

    @Nullable
    T deserialize(byte[] bytes);
}
