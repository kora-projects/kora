package io.koraframework.validation.module;

import org.jspecify.annotations.Nullable;
import io.koraframework.validation.common.constraint.ValidatorModule;
import io.koraframework.validation.module.http.server.ValidationHttpServerInterceptor;
import io.koraframework.validation.module.http.server.ViolationExceptionHttpServerResponseMapper;

public interface ValidationModule extends ValidatorModule {

    default ValidationHttpServerInterceptor validationHttpServerInterceptor(@Nullable ViolationExceptionHttpServerResponseMapper mapper) {
        return new ValidationHttpServerInterceptor(mapper);
    }
}
