package io.koraframework.soap.client.common;

import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.koraframework.soap.client.common.exception.SoapException;
import io.koraframework.soap.client.common.util.MultipartParserUtils;

import java.io.InputStream;
import java.util.Map;

public interface SoapEnvelopeMapper {

    byte[] marshal(SoapEnvelope envelope) throws SoapException;

    SoapEnvelope unmarshal(InputStream is) throws SoapException;

    Object unmarshal(Map<String, MultipartParserUtils.Part> parts, String xmlPartId) throws SoapException;
}
