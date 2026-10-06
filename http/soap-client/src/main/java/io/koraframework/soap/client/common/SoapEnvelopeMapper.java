package io.koraframework.soap.client.common;

import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.koraframework.soap.client.common.exception.SoapException;
import io.koraframework.soap.client.common.util.MultipartParserUtils;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;

public interface SoapEnvelopeMapper {

    byte[] marshal(SoapEnvelope envelope) throws SoapException;

    SoapEnvelope unmarshal(InputStream is) throws SoapException;

    /**
     * @param charset charset from the response Content-Type, if any; when null the encoding is detected from the XML itself
     */
    default SoapEnvelope unmarshal(InputStream is, @Nullable Charset charset) throws SoapException {
        return unmarshal(is);
    }

    Object unmarshal(Map<String, MultipartParserUtils.Part> parts, String xmlPartId) throws SoapException;
}
