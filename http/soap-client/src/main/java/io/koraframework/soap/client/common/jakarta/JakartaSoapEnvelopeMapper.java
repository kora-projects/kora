package io.koraframework.soap.client.common.jakarta;

import io.koraframework.soap.client.common.*;
import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.koraframework.soap.client.common.exception.SoapException;
import io.koraframework.soap.client.common.exception.SoapRequestMarshallingException;
import io.koraframework.soap.client.common.exception.SoapResponseUnmarshallingException;
import io.koraframework.soap.client.common.util.MultipartParserUtils;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
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
        return unmarshal(is, null);
    }

    @Override
    public SoapEnvelope unmarshal(InputStream is, @Nullable Charset charset) throws SoapException {
        try {
            var unmarshaller = jaxb.createUnmarshaller();
            if (charset == null) {
                return (SoapEnvelope) unmarshaller.unmarshal(is);
            }
            // byte stream + declared encoding: the parser still skips a byte order mark, unlike a Reader
            var source = new InputSource(is);
            source.setEncoding(charset.name());
            return (SoapEnvelope) unmarshaller.unmarshal(new SAXSource(source));
        } catch (jakarta.xml.bind.JAXBException e) {
            throw new SoapResponseUnmarshallingException(e);
        }
    }

    @Override
    public SoapEnvelope unmarshal(Map<String, MultipartParserUtils.Part> parts, String xmlPartId) throws SoapException {
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
