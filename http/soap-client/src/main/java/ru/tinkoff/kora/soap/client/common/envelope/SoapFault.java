package ru.tinkoff.kora.soap.client.common.envelope;


import javax.xml.namespace.QName;


@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
@javax.xml.bind.annotation.XmlAccessorType(javax.xml.bind.annotation.XmlAccessType.FIELD)
@jakarta.xml.bind.annotation.XmlType(name = "Fault", propOrder = {
    "faultcode",
    "faultstring",
    "faultactor",
    "detail"
})
@javax.xml.bind.annotation.XmlType(name = "Fault", propOrder = {
    "faultcode",
    "faultstring",
    "faultactor",
    "detail"
})
@jakarta.xml.bind.annotation.XmlRootElement(namespace = "http://schemas.xmlsoap.org/soap/envelope/", name = "Fault")
@javax.xml.bind.annotation.XmlRootElement(namespace = "http://schemas.xmlsoap.org/soap/envelope/", name = "Fault")
public class SoapFault {

    @jakarta.xml.bind.annotation.XmlElement(required = true)
    @javax.xml.bind.annotation.XmlElement(required = true)
    protected QName faultcode;
    @jakarta.xml.bind.annotation.XmlElement(required = true)
    @javax.xml.bind.annotation.XmlElement(required = true)
    protected String faultstring;
    @jakarta.xml.bind.annotation.XmlSchemaType(name = "anyURI")
    @javax.xml.bind.annotation.XmlSchemaType(name = "anyURI")
    protected String faultactor;
    protected SoapFaultDetail detail;


    public QName getFaultcode() {
        return faultcode;
    }

    public void setFaultcode(QName value) {
        this.faultcode = value;
    }

    public String getFaultstring() {
        return faultstring;
    }

    public void setFaultstring(String value) {
        this.faultstring = value;
    }

    public String getFaultactor() {
        return faultactor;
    }

    public void setFaultactor(String value) {
        this.faultactor = value;
    }

    public SoapFaultDetail getDetail() {
        return detail;
    }

    public void setDetail(SoapFaultDetail value) {
        this.detail = value;
    }

}
