package io.koraframework.common.liveness;

/**
 * @param message <br>
 *                <b>Русский</b>: сообщение об ошибке
 *                <hr>
 *                <b>English</b>: message about failure
 */
public record LivenessProbeFailure(String message) {}
