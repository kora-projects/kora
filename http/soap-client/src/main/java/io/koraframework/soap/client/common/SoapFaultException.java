package io.koraframework.soap.client.common;

import io.koraframework.soap.client.common.envelope.SoapFault;

public class SoapFaultException extends SoapException {
    private final SoapFault fault;

    public SoapFaultException(String message, SoapFault fault) {
        super(message);
        this.fault = fault;
    }

    public SoapFault getFault() {
        return fault;
    }
}
