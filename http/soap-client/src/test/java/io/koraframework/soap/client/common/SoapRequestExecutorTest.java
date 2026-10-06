package io.koraframework.soap.client.common;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.koraframework.soap.client.common.exception.SoapException;
import io.koraframework.soap.client.common.jakarta.JakartaSoapEnvelopeMapper;
import io.koraframework.soap.client.common.telemetry.SoapClientTelemetryConfig;
import io.koraframework.soap.client.common.telemetry.SoapClientTelemetryFactory;
import io.koraframework.soap.client.common.telemetry.impl.NoopSoapClientTelemetry;
import jakarta.xml.bind.JAXBContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoapRequestExecutorTest {

    private static final String EMPTY_BODY = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body/></soap:Envelope>";

    @Test
    void emptySoapBodyOn200IsSuccessWithNullBody() throws Exception {
        var result = executor(200, EMPTY_BODY).call(new SoapEnvelope());
        assertThat(result).isEqualTo(new SoapResult.Success(null));
    }

    @Test
    void emptySoapBodyOn500IsSoapException() throws Exception {
        var executor = executor(500, EMPTY_BODY);
        assertThatThrownBy(() -> executor.call(new SoapEnvelope()))
            .isExactlyInstanceOf(SoapException.class)
            .hasMessageContaining("SOAP fault expected");
    }

    private static SoapRequestExecutor executor(int code, String xml) throws Exception {
        HttpClient http = request -> new HttpClientResponse() {
            @Override public int code() { return code; }
            @Override public HttpHeaders headers() { return HttpHeaders.of("content-type", "text/xml; charset=utf-8"); }
            @Override public HttpBodyInput body() { return HttpBody.of(xml.getBytes(StandardCharsets.UTF_8)); }
            @Override public void close() {}
        };
        SoapClientTelemetryFactory tf = (p, c, cfg, d, u) -> NoopSoapClientTelemetry.INSTANCE;
        var config = new SoapServiceConfig() {
            @Override public String url() { return "http://localhost/ws"; }
            @Override public SoapClientTelemetryConfig telemetry() { return null; }
        };
        var mapper = new JakartaSoapEnvelopeMapper(JAXBContext.newInstance(SoapEnvelope.class));
        return new SoapRequestExecutor(http, tf, mapper, config, "soap", new SoapMethodDescriptor("Svc", "Svc", "ping", null));
    }
}
