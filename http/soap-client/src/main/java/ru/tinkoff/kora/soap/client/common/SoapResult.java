package ru.tinkoff.kora.soap.client.common;

import ru.tinkoff.kora.soap.client.common.envelope.SoapFault;

public sealed interface SoapResult {
    record Success(Object body) implements SoapResult {}

    record Failure(SoapFault fault, String faultMessage) implements SoapResult {}
}
