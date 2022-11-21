package ru.tinkoff.kora.soap.client.common.envelope;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
@javax.xml.bind.annotation.XmlAccessorType(javax.xml.bind.annotation.XmlAccessType.FIELD)
@jakarta.xml.bind.annotation.XmlType(name = "detail", propOrder = {
    "any"
})
@javax.xml.bind.annotation.XmlType(name = "detail", propOrder = {
    "any"
})
public class SoapFaultDetail {

    @jakarta.xml.bind.annotation.XmlAnyElement(lax = true)
    @javax.xml.bind.annotation.XmlAnyElement(lax = true)
    protected List<Object> any;
    @jakarta.xml.bind.annotation.XmlAnyAttribute
    @javax.xml.bind.annotation.XmlAnyAttribute
    private Map<QName, String> otherAttributes = new HashMap<QName, String>();

    public List<Object> getAny() {
        if (any == null) {
            any = new ArrayList<Object>();
        }
        return this.any;
    }

    public Map<QName, String> getOtherAttributes() {
        return otherAttributes;
    }

}
