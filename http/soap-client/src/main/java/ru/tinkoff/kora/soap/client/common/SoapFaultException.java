package ru.tinkoff.kora.soap.client.common;

import ru.tinkoff.kora.soap.client.common.envelope.SoapFault;

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
