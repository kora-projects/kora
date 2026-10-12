package io.koraframework.soap.client.common;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.koraframework.soap.client.common.exception.SoapInvalidHttpResponseException;
import io.koraframework.soap.client.common.jakarta.JakartaSoapEnvelopeMapper;
import io.koraframework.soap.client.common.telemetry.SoapClientObservation;
import io.koraframework.soap.client.common.telemetry.SoapClientTelemetryConfig;
import io.opentelemetry.api.trace.Span;
import jakarta.xml.bind.JAXBContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoapRequestExecutorTest {

    private final SoapClientObservation observation = Mockito.mock(SoapClientObservation.class);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "500 | text/html  | <html><body><h1>upstream db-host-17 refused connection</h1></body></html>",
        "500 | text/plain | Internal Server Error: db-host-17 down",
        "500 | text/xml   | <?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><x>db-host-17</x></s:Body></s:Envelope>",
        "500 | text/xml   | <s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body/></s:Envelope><!-- db-host-17 -->",
        "503 | text/html  | <html><body><h1>upstream db-host-17 refused connection</h1></body></html>",
    })
    void nonFaultErrorResponseIsInvalidHttpResponseWithCodeAndBody(int code, String contentType, String body) throws Exception {
        assertThatThrownBy(() -> executor(code, contentType, body).call(new SoapEnvelope()))
            .isInstanceOf(SoapInvalidHttpResponseException.class)
            .hasMessageContaining(String.valueOf(code))
            .hasMessageContaining("db-host-17");
        verify(observation).observeResponseBody(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void emptyErrorResponseIsInvalidHttpResponseWithCode() throws Exception {
        assertThatThrownBy(() -> executor(500, "text/xml", "").call(new SoapEnvelope()))
            .isInstanceOf(SoapInvalidHttpResponseException.class)
            .hasMessageContaining("500");
        verify(observation).observeResponseBody(new byte[0]);
    }

    @Test
    void faultResponseIsFailure() throws Exception {
        var body = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault>
            <faultcode>s:Server</faultcode><faultstring>boom</faultstring>
            </s:Fault></s:Body></s:Envelope>""";

        var result = executor(500, "text/xml", body).call(new SoapEnvelope());

        assertThat(result).isInstanceOfSatisfying(SoapResult.Failure.class, f -> assertThat(f.faultMessage()).contains("boom"));
        verify(observation).observeResponseBody(body.getBytes(StandardCharsets.UTF_8));
    }

    private SoapRequestExecutor executor(int code, String contentType, String body) throws Exception {
        when(observation.span()).thenReturn(Span.getInvalid());
        HttpClient http = request -> new HttpClientResponse() {
            @Override
            public int code() { return code; }

            @Override
            public HttpHeaders headers() { return HttpHeaders.of("content-type", contentType); }

            @Override
            public HttpBodyInput body() { return HttpBody.of(body.getBytes(StandardCharsets.UTF_8)); }

            @Override
            public void close() {}
        };
        var config = new SoapServiceConfig() {
            @Override
            public String url() { return "http://localhost/ws"; }

            @Override
            public SoapClientTelemetryConfig telemetry() { return null; }
        };
        var mapper = new JakartaSoapEnvelopeMapper(JAXBContext.newInstance(SoapEnvelope.class));
        return new SoapRequestExecutor(http, (path, clazz, cfg, descriptor, url) -> envelope -> observation,
            mapper, config, "soap", new SoapMethodDescriptor("Svc", "Svc", "ping", null));
    }
}
