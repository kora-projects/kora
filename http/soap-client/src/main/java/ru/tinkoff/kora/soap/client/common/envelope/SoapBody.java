package ru.tinkoff.kora.soap.client.common.envelope;


import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
@javax.xml.bind.annotation.XmlAccessorType(javax.xml.bind.annotation.XmlAccessType.FIELD)
@jakarta.xml.bind.annotation.XmlType(name = "Body", propOrder = "any")
@javax.xml.bind.annotation.XmlType(name = "Body", propOrder = "any")
@jakarta.xml.bind.annotation.XmlRootElement(namespace = "http://schemas.xmlsoap.org/soap/envelope/", name = "Body")
@javax.xml.bind.annotation.XmlRootElement(namespace = "http://schemas.xmlsoap.org/soap/envelope/", name = "Body")
public class SoapBody {

    @jakarta.xml.bind.annotation.XmlAnyElement(lax = true)
    @javax.xml.bind.annotation.XmlAnyElement(lax = true)
    protected List<Object> any;
    @jakarta.xml.bind.annotation.XmlAnyAttribute
    @javax.xml.bind.annotation.XmlAnyAttribute
    private Map<QName, String> otherAttributes = new HashMap<QName, String>();

    public SoapBody() {
    }

    public SoapBody(Object any) {
        this.getAny().add(any);
    }

    public List<Object> getAny() {
        if (any == null) {
            any = new ArrayList<>();
        }
        return this.any;
    }

    public Map<QName, String> getOtherAttributes() {
        return otherAttributes;
    }

}
