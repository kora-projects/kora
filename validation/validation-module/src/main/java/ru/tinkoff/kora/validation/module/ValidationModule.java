package ru.tinkoff.kora.validation.module;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;
import ru.tinkoff.kora.validation.module.http.server.ValidationHttpServerInterceptor;
import ru.tinkoff.kora.validation.module.http.server.ViolationExceptionHttpServerResponseMapper;

public interface ValidationModule extends ValidatorModule {

    default ValidationHttpServerInterceptor validationHttpServerInterceptor(@Nullable ViolationExceptionHttpServerResponseMapper mapper) {
        return new ValidationHttpServerInterceptor(mapper);
    }
}
