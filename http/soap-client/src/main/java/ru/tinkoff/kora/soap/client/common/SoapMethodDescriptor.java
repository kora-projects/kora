package ru.tinkoff.kora.soap.client.common;

public class SoapMethodDescriptor {
    public final String serviceClass;
    public final String service;
    public final String method;
    public final String soapAction;

    public SoapMethodDescriptor(String serviceClass, String service, String method, String soapAction) {
        this.serviceClass = serviceClass;
        this.service = service;
        this.method = method;
        this.soapAction = soapAction;
    }
}
