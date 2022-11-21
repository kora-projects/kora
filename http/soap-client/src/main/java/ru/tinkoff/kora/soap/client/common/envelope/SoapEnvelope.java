package ru.tinkoff.kora.soap.client.common.envelope;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
@javax.xml.bind.annotation.XmlAccessorType(javax.xml.bind.annotation.XmlAccessType.FIELD)
@jakarta.xml.bind.annotation.XmlType(name = "Envelope", propOrder = {
    "header",
    "body",
    "any"
})
@javax.xml.bind.annotation.XmlType(name = "Envelope", propOrder = {
    "header",
    "body",
    "any"
})
@jakarta.xml.bind.annotation.XmlRootElement(namespace = "http://schemas.xmlsoap.org/soap/envelope/", name = "Envelope")
@javax.xml.bind.annotation.XmlRootElement(namespace = "http://schemas.xmlsoap.org/soap/envelope/", name = "Envelope")
public class SoapEnvelope {

    @jakarta.xml.bind.annotation.XmlElement(name = "Header", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
    @javax.xml.bind.annotation.XmlElement(name = "Header", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
    protected SoapEnvelopeHeader header = new SoapEnvelopeHeader();
    @jakarta.xml.bind.annotation.XmlElement(name = "Body", namespace = "http://schemas.xmlsoap.org/soap/envelope/", required = true)
    @javax.xml.bind.annotation.XmlElement(name = "Body", namespace = "http://schemas.xmlsoap.org/soap/envelope/", required = true)
    protected SoapBody body = new SoapBody();
    @jakarta.xml.bind.annotation.XmlAnyElement(lax = true)
    @javax.xml.bind.annotation.XmlAnyElement(lax = true)
    protected List<Object> any = new ArrayList<>();
    @jakarta.xml.bind.annotation.XmlAnyAttribute
    @javax.xml.bind.annotation.XmlAnyAttribute
    private Map<QName, String> otherAttributes = new HashMap<>();

    public SoapEnvelope() {
    }

    public SoapEnvelope(Object bodyContent) {
        this.body.getAny().add(bodyContent);
    }

    public SoapEnvelopeHeader getHeader() {
        return header;
    }

    public void setHeader(SoapEnvelopeHeader value) {
        this.header = value;
    }

    public SoapBody getBody() {
        return body;
    }

    public void setBody(SoapBody value) {
        this.body = value;
    }

    public List<Object> getAny() {
        return this.any;
    }

    public Map<QName, String> getOtherAttributes() {
        return this.otherAttributes;
    }
}
