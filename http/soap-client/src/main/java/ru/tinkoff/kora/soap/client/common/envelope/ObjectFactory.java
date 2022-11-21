package ru.tinkoff.kora.soap.client.common.envelope;


@jakarta.xml.bind.annotation.XmlRegistry
@javax.xml.bind.annotation.XmlRegistry
public class ObjectFactory {

    public ObjectFactory() {
    }

    public SoapEnvelope createEnvelope() {
        return new SoapEnvelope();
    }

    public SoapEnvelopeHeader createHeader() {
        return new SoapEnvelopeHeader();
    }

    public SoapBody createBody() {
        return new SoapBody();
    }

    public SoapFault createFault() {
        return new SoapFault();
    }

    public SoapFaultDetail createDetail() {
        return new SoapFaultDetail();
    }
}
