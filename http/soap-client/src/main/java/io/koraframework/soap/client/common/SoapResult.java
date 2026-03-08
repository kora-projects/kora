package io.koraframework.soap.client.common;

import io.koraframework.soap.client.common.envelope.SoapFault;

public sealed interface SoapResult {

    record Success(Object body) implements SoapResult {}

    record Failure(SoapFault fault, String faultMessage) implements SoapResult {}
}
