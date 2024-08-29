package ru.tinkoff.kora.common.liveness;

import jakarta.annotation.Nullable;

/**
 * <b>Русский</b>: Проверяет жизнеспособность сервиса
 * <hr>
 * <b>English</b>: Perform liveness probe
 */
public interface LivenessProbe {

    /**
     * @return <b>Русский</b>: null в случае успеха пробы или ответ с сообщением об ошибке
     * <hr>
     * <b>English</b>: null if probe succeeds or probe failure
     */
    @Nullable
    LivenessProbeFailure probe() throws Exception;
}
