package io.koraframework.soap.client.common;

import io.koraframework.soap.client.common.envelope.SoapEnvelope;

import java.io.InputStream;
import java.util.Map;

public interface XmlTools {
    byte[] marshal(SoapEnvelope envelope) throws SoapException;

    SoapEnvelope unmarshal(InputStream is) throws SoapException;

    Object unmarshal(Map<String, MultipartParser.Part> parts, String xmlPartId) throws SoapException;

}
