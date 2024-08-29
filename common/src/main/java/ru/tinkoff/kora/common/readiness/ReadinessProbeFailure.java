package ru.tinkoff.kora.common.readiness;

/**
 * @param message <br>
 *                <b>Русский</b>: сообщение об ошибке
 *                <hr>
 *                <b>English</b>: message about failure
 */
public record ReadinessProbeFailure(String message) {}
