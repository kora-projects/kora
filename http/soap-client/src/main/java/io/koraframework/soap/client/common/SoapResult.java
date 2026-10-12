package io.koraframework.soap.client.common;

import io.koraframework.soap.client.common.envelope.SoapFault;
import org.jspecify.annotations.Nullable;

public sealed interface SoapResult {

    record Success(@Nullable Object body) implements SoapResult {}

    record Failure(SoapFault fault, String faultMessage) implements SoapResult {}
}
