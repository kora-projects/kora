package ru.tinkoff.kora.soap.client.common.javax;

import ru.tinkoff.kora.soap.client.common.*;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

public class JavaxXmlTools implements XmlTools {
    private final javax.xml.bind.JAXBContext jaxb;

    public JavaxXmlTools(javax.xml.bind.JAXBContext jaxb) {
        this.jaxb = jaxb;
    }

    @Override
    public byte[] marshal(SoapEnvelope envelope) throws SoapException {
        var baos = new ByteArrayOutputStream();
        try {
            var marshaller = this.jaxb.createMarshaller();
            marshaller.marshal(envelope, baos);
        } catch (javax.xml.bind.JAXBException e) {
            throw new SoapRequestMarshallingException(e);
        }
        return baos.toByteArray();
    }

    @Override
    public SoapEnvelope unmarshal(InputStream is) throws SoapException {
        try {
            var unmarshaller = jaxb.createUnmarshaller();
            return (SoapEnvelope) unmarshaller.unmarshal(is);
        } catch (javax.xml.bind.JAXBException e) {
            throw new SoapResponseUnmarshallingException(e);
        }
    }

    @Override
    public SoapEnvelope unmarshal(Map<String, MultipartParser.Part> parts, String xmlPartId) throws SoapException {
        var xmlPart = parts.get(xmlPartId);
        try {
            var unmarshaller = jaxb.createUnmarshaller();
            unmarshaller.setAttachmentUnmarshaller(new JavaxXopAttachmentUnmarshaller(parts));
            return (SoapEnvelope) unmarshaller.unmarshal(xmlPart.getContentStream());
        } catch (javax.xml.bind.JAXBException e) {
            throw new SoapResponseUnmarshallingException(e);
        }
    }
}
