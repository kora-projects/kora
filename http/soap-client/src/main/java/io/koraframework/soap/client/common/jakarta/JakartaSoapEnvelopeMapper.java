package io.koraframework.soap.client.common.jakarta;

import io.koraframework.soap.client.common.*;
import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.koraframework.soap.client.common.exception.SoapException;
import io.koraframework.soap.client.common.exception.SoapRequestMarshallingException;
import io.koraframework.soap.client.common.exception.SoapResponseUnmarshallingException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

public class JakartaSoapEnvelopeMapper implements SoapEnvelopeMapper {
    private final jakarta.xml.bind.JAXBContext jaxb;

    public JakartaSoapEnvelopeMapper(jakarta.xml.bind.JAXBContext jaxb) {
        this.jaxb = jaxb;
    }

    @Override
    public byte[] marshal(SoapEnvelope envelope) throws SoapException {
        var baos = new ByteArrayOutputStream();
        try {
            var marshaller = this.jaxb.createMarshaller();
            marshaller.marshal(envelope, baos);
        } catch (jakarta.xml.bind.JAXBException e) {
            throw new SoapRequestMarshallingException(e);
        }
        return baos.toByteArray();
    }

    @Override
    public SoapEnvelope unmarshal(InputStream is) throws SoapException {
        try {
            var unmarshaller = jaxb.createUnmarshaller();
            return (SoapEnvelope) unmarshaller.unmarshal(is);
        } catch (jakarta.xml.bind.JAXBException e) {
            throw new SoapResponseUnmarshallingException(e);
        }
    }

    @Override
    public SoapEnvelope unmarshal(Map<String, MultipartParser.Part> parts, String xmlPartId) throws SoapException {
        var xmlPart = parts.get(xmlPartId);
        try {
            var unmarshaller = jaxb.createUnmarshaller();
            unmarshaller.setAttachmentUnmarshaller(new JakartaXopAttachmentUnmarshaller(parts));
            return (SoapEnvelope) unmarshaller.unmarshal(xmlPart.getContentStream());
        } catch (jakarta.xml.bind.JAXBException e) {
            throw new SoapResponseUnmarshallingException(e);
        }
    }
}
