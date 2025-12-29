package ru.tinkoff.kora.common.readiness;

import org.jspecify.annotations.Nullable;

/**
 * <b>Русский</b>: Проверяет готовность сервиса к работе
 * <hr>
 * <b>English</b>: Perform readiness probe
 */
public interface ReadinessProbe {

    /**
     * @return <b>Русский</b>: null в случае успеха пробы или ответ с сообщением об ошибке
     * <hr>
     * <b>English</b>: null if probe succeeds or probe failure
     */
    @Nullable
    ReadinessProbeFailure probe() throws Exception;
}
