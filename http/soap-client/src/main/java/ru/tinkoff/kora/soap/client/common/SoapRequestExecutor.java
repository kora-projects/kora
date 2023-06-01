package ru.tinkoff.kora.soap.client.common;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.BlockingHttpResponse;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;
import ru.tinkoff.kora.soap.client.common.envelope.SoapFault;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.InternalServerError;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.InvalidHttpCode;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.ProcessException;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetryFactory;

import javax.annotation.Nullable;
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

    public SoapRequestExecutor(HttpClient httpClient, SoapClientTelemetryFactory telemetryFactory, XmlTools xmlTools, String service, String url, Duration timeout, String method, @Nullable String soapAction) {
        this.httpClient = httpClient;
        this.xmlTools = xmlTools;
        this.url = url;
        this.timeout = timeout;
        this.soapAction = soapAction;
        this.telemetry = telemetryFactory.get(service, method, url);
    }

    public SoapResult call(SoapEnvelope requestEnvelope) throws SoapException {
        var telemetry = this.telemetry.get(requestEnvelope);
        var requestXml = this.xmlTools.marshal(requestEnvelope);
        var httpClientRequest = HttpClientRequest.post(this.url)
            .body(requestXml)
            .requestTimeout((int) timeout.toMillis())
            .header("content-type", "text/xml");
        if (this.soapAction != null) {
            httpClientRequest.header("SOAPAction", this.soapAction);
        }
        try (var httpClientResponse = BlockingHttpResponse.from(this.httpClient.execute(httpClientRequest.build()))) {
            var body = httpClientResponse.body();
            if (httpClientResponse.code() != 200 && httpClientResponse.code() != 500) {
                telemetry.failure(new InvalidHttpCode(httpClientResponse.code()));
                throw parseInvalidHttpCodeResponse(httpClientResponse);
            }
            if (httpClientResponse.code() == 200) {
                var contentType = httpClientResponse.headers().getFirst("content-type");
                if (contentType != null && contentType.toLowerCase().startsWith("multipart")) {
                    var result = readMultipart(contentType, body);
                    telemetry.success(result);
                    return result;
                } else {
                    var responseEnvelope = this.xmlTools.unmarshal(body);
                    var result = new SoapResult.Success(responseEnvelope.getBody().getAny().get(0));
                    telemetry.success(result);
                    return result;
                }
            }
            var result = readFailure(body);
            telemetry.failure(new InternalServerError(result));
            return result;
        } catch (IOException | HttpClientException e) {
            telemetry.failure(new ProcessException(e));
            throw new SoapException(e);
        } catch (Exception e) {
            telemetry.failure(new ProcessException(e));
            throw e;
        }
    }

    public Mono<SoapResult> callReactive(SoapEnvelope requestEnvelope) {
        return Mono.defer(() -> {
            var telemetry = this.telemetry.get(requestEnvelope);
            var requestXml = this.xmlTools.marshal(requestEnvelope);
            var httpClientRequest = HttpClientRequest.post(this.url)
                .body(requestXml)
                .header("content-type", "text/xml");
            if (this.soapAction != null) {
                httpClientRequest.header("SOAPAction", this.soapAction);
            }
            return this.httpClient.execute(httpClientRequest.build())
                .doOnError(e -> telemetry.failure(new ProcessException(e)))
                .flatMap(httpClientResponse -> {
                    if (httpClientResponse.code() != 200 && httpClientResponse.code() != 500) {
                        return ReactorUtils.toByteArrayMono(httpClientResponse.body())
                            .handle((body, sink) -> {
                                telemetry.failure(new InvalidHttpCode(httpClientResponse.code()));
                                sink.error(new InvalidHttpResponseSoapException(httpClientResponse.code(), body));
                            });
                    }
                    return ReactorUtils.toByteArrayMono(httpClientResponse.body())
                        .onErrorMap(e -> {
                            telemetry.failure(new ProcessException(e));
                            return new SoapException(e);
                        })
                        .map(body -> {
                            try {
                                if (httpClientResponse.code() == 200) {
                                    var contentType = httpClientResponse.headers().getFirst("content-type");
                                    if (contentType != null && contentType.toLowerCase().startsWith("multipart")) {
                                        var result = readMultipart(contentType, new ByteArrayInputStream(body));
                                        telemetry.success(result);
                                        return result;
                                    } else {
                                        var responseEnvelope = this.xmlTools.unmarshal(new ByteArrayInputStream(body));
                                        var result = new SoapResult.Success(responseEnvelope.getBody().getAny().get(0));
                                        telemetry.success(result);
                                        return result;
                                    }
                                }

                                var result = readFailure(new ByteArrayInputStream(body));
                                telemetry.failure(new InternalServerError(result));
                                return result;
                            } catch (IOException e) {
                                telemetry.failure(new ProcessException(e));
                                throw new SoapException(e);
                            }
                        });
                });
        });
    }

    private SoapException parseInvalidHttpCodeResponse(BlockingHttpResponse httpClientResponse) {
        try {
            var responseBody = httpClientResponse.body().readAllBytes();
            return new InvalidHttpResponseSoapException(httpClientResponse.code(), responseBody);
        } catch (IOException e) {
            return new SoapException(e);
        }
    }

    private SoapResult.Success readMultipart(String contentType, InputStream body) throws IOException {
        var multipartMeta = MultipartParser.parseMeta(contentType);
        var parts = MultipartParser.parse(body.readAllBytes(), multipartMeta.boundary());
        var xmlPartId = multipartMeta.start();
        var responseEnvelope = (SoapEnvelope) this.xmlTools.unmarshal(parts, xmlPartId);
        var responseBody = responseEnvelope.getBody().getAny().get(0);
        return new SoapResult.Success(responseBody);
    }

    private SoapResult.Failure readFailure(InputStream body) throws IOException {
        var responseEnvelope = this.xmlTools.unmarshal(body);
        var fault = (SoapFault) responseEnvelope.getBody().getAny().get(0);
        var faultMessage = fault.getFaultcode().toString() + " " + fault.getFaultstring();
        return new SoapResult.Failure(fault, faultMessage);
    }
}
