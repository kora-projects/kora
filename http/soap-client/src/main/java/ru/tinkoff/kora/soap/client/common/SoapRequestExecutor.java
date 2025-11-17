package ru.tinkoff.kora.soap.client.common;

import io.opentelemetry.context.Context;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;
import ru.tinkoff.kora.soap.client.common.envelope.SoapFault;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetryFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

public class SoapRequestExecutor {
    private final HttpClient httpClient;
    private final XmlTools xmlTools;
    private final String url;
    private final String soapAction;
    private final SoapClientTelemetry telemetry;
    private final Duration timeout;

    public SoapRequestExecutor(HttpClient httpClient, SoapClientTelemetryFactory telemetryFactory, XmlTools xmlTools, SoapServiceConfig config, SoapMethodDescriptor methodDescriptor) {
        this.httpClient = httpClient;
        this.xmlTools = xmlTools;
        this.url = config.url();
        this.timeout = config.timeout();
        this.soapAction = methodDescriptor.soapAction;
        this.telemetry = telemetryFactory.get(config.telemetry(), methodDescriptor, url);
    }

    public SoapResult call(SoapEnvelope requestEnvelope) throws SoapException {
        var observation = this.telemetry.observe(requestEnvelope);
        return ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> {
                try {
                    observation.observeRequest(requestEnvelope);
                    var requestXml = this.xmlTools.marshal(requestEnvelope);
                    observation.observeRequestXml(requestXml);
                    var httpClientRequest = HttpClientRequest.post(this.url)
                        .body(HttpBody.of("text/xml", requestXml))
                        .requestTimeout((int) timeout.toMillis());
                    if (this.soapAction != null) {
                        httpClientRequest.header("SOAPAction", this.soapAction);
                    }
                    try (var httpClientResponse = this.httpClient.execute(httpClientRequest.build());
                         var body = httpClientResponse.body();
                         var is = body.asInputStream()) {
                        observation.observeHttpResponse(httpClientResponse);

                        if (httpClientResponse.code() != 200 && httpClientResponse.code() != 500) {
                            try {
                                var bodyAsBytes = is.readAllBytes();
                                observation.observeResponseBody(bodyAsBytes);
                                throw new InvalidHttpResponseSoapException(httpClientResponse.code(), bodyAsBytes);
                            } catch (IOException e) {
                                var ex = new InvalidHttpResponseSoapException(httpClientResponse.code(), new byte[0]);
                                ex.addSuppressed(e);
                                throw ex;
                            }
                        }
                        if (httpClientResponse.code() != 200) {
                            var bytes = is.readAllBytes();
                            var result = readFailure(new ByteArrayInputStream(bytes));
                            observation.observeResponseBody(bytes);
                            observation.observeFailure(result);
                            return result;
                        }
                        var contentType = httpClientResponse.headers().getFirst("content-type");
                        if (contentType != null && contentType.toLowerCase().startsWith("multipart")) {
                            var result = readMultipart(contentType, is);
                            observation.observeResponseBody(result.xmlPart().getContentArray());
                            observation.observeResult(result.result.body());
                            return result.result;
                        } else {
                            var xml = is.readAllBytes();
                            observation.observeResponseBody(xml);
                            var success = readSuccess(new ByteArrayInputStream(xml));
                            observation.observeResult(success.body());
                            return success;
                        }
                    } catch (IOException | HttpClientException e) {
                        throw new SoapException(e);
                    }
                } catch (Throwable t) {
                    observation.observeError(t);
                    throw t;
                } finally {
                    observation.end();
                }
            });

    }

    private SoapResult.Success readSuccess(InputStream body) throws IOException {
        var bodyAsBytes = body.readAllBytes();
        try (var bi = new ByteArrayInputStream(bodyAsBytes)) {
            var responseEnvelope = this.xmlTools.unmarshal(bi);
            return new SoapResult.Success(responseEnvelope.getBody().getAny().get(0));
        }
    }

    private record ParseMultipartResult(SoapResult.Success result, MultipartParser.Part xmlPart) {}

    private ParseMultipartResult readMultipart(String contentType, InputStream body) throws IOException {
        var multipartMeta = MultipartParser.parseMeta(contentType);
        var bodyAsBytes = body.readAllBytes();
        var parts = MultipartParser.parse(bodyAsBytes, multipartMeta.boundary());
        var xmlPartId = multipartMeta.start();
        var responseEnvelope = (SoapEnvelope) this.xmlTools.unmarshal(parts, xmlPartId);
        var responseBody = responseEnvelope.getBody().getAny().get(0);
        return new ParseMultipartResult(new SoapResult.Success(responseBody), parts.get(xmlPartId));
    }

    private SoapResult.Failure readFailure(InputStream body) throws IOException {
        var responseEnvelope = this.xmlTools.unmarshal(body);
        var fault = (SoapFault) responseEnvelope.getBody().getAny().get(0);
        var faultMessage = fault.getFaultcode().toString() + " " + fault.getFaultstring();
        return new SoapResult.Failure(fault, faultMessage);
    }
}
