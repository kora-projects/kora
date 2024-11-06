package ru.tinkoff.kora.soap.client.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;
import ru.tinkoff.kora.soap.client.common.envelope.SoapFault;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.InternalServerError;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.InvalidHttpCode;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.ProcessException;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetryFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class SoapRequestExecutor {
    private final HttpClient httpClient;
    private final XmlTools xmlTools;
    private final String url;
    private final String soapAction;
    private final SoapClientTelemetry telemetry;
    private final Duration timeout;

    @Deprecated
    public SoapRequestExecutor(HttpClient httpClient, SoapClientTelemetryFactory telemetryFactory, XmlTools xmlTools, String service, SoapServiceConfig config, String method, @Nullable String soapAction) {
        this(httpClient, telemetryFactory, xmlTools, "ru.tinkoff.kora.soap.client." + service, service, config, method, soapAction);
    }

    public SoapRequestExecutor(HttpClient httpClient, SoapClientTelemetryFactory telemetryFactory, XmlTools xmlTools, String serviceClass, String service, SoapServiceConfig config, String method, @Nullable String soapAction) {
        this.httpClient = httpClient;
        this.xmlTools = xmlTools;
        this.url = config.url();
        this.timeout = config.timeout();
        this.soapAction = soapAction;
        this.telemetry = telemetryFactory.get(config.telemetry(), serviceClass, service, method, url);
    }

    public SoapResult call(SoapEnvelope requestEnvelope) throws SoapException {
        var telemetry = this.telemetry.get(requestEnvelope);
        var requestXml = this.xmlTools.marshal(requestEnvelope);
        telemetry.prepared(requestEnvelope, requestXml);
        var httpClientRequest = HttpClientRequest.post(this.url)
            .body(HttpBody.of("text/xml", requestXml))
            .requestTimeout((int) timeout.toMillis());
        if (this.soapAction != null) {
            httpClientRequest.header("SOAPAction", this.soapAction);
        }
        try (var httpClientResponse = this.httpClient.execute(httpClientRequest.build()).toCompletableFuture().get()) {
            if (httpClientResponse.code() != 200 && httpClientResponse.code() != 500) {
                telemetry.failure(new InvalidHttpCode(httpClientResponse.code()));
                throw parseInvalidHttpCodeResponse(httpClientResponse);
            }
            try (var body = httpClientResponse.body();
                 var is = body.asInputStream()) {
                if (httpClientResponse.code() == 200) {
                    var contentType = httpClientResponse.headers().getFirst("content-type");
                    final SoapResult.Success result;
                    if (contentType != null && contentType.toLowerCase().startsWith("multipart")) {
                        result = readMultipart(contentType, is);
                    } else {
                        result = readSuccess(is);
                    }
                    telemetry.success(result);
                    return result;
                }
                var result = readFailure(is);
                telemetry.failure(new InternalServerError(result));
                return result;
            }
        } catch (IOException | HttpClientException | InterruptedException e) {
            telemetry.failure(new ProcessException(e));
            throw new SoapException(e);
        } catch (ExecutionException e) {
            if (e.getCause() != null) {
                telemetry.failure(new ProcessException(e.getCause()));
                if (e.getCause() instanceof IOException || e.getCause() instanceof HttpClientException) {
                    throw new SoapException(e.getCause());
                } else if (e.getCause() instanceof RuntimeException re) {
                    throw re;
                }
            } else {
                telemetry.failure(new ProcessException(e));
            }
            throw new SoapException(e);
        } catch (Exception e) {
            telemetry.failure(new ProcessException(e));
            throw e;
        }
    }

    public CompletionStage<SoapResult> callAsync(SoapEnvelope requestEnvelope) {
        var telemetry = this.telemetry.get(requestEnvelope);
        var requestXml = this.xmlTools.marshal(requestEnvelope);
        telemetry.prepared(requestEnvelope, requestXml);
        var httpClientRequest = HttpClientRequest.post(this.url)
            .body(HttpBody.of("text/xml", requestXml));
        if (this.soapAction != null) {
            httpClientRequest.header("SOAPAction", this.soapAction);
        }
        return this.httpClient.execute(httpClientRequest.build())
            .whenComplete((response, error) -> {
                if (error != null) {
                    telemetry.failure(new ProcessException(error));
                }
            })
            .thenCompose(httpClientResponse -> {
                if (httpClientResponse.code() != 200 && httpClientResponse.code() != 500) {
                    return FlowUtils.toByteArrayFuture(httpClientResponse.body())
                        .handle((body, sink) -> {
                            telemetry.failure(new InvalidHttpCode(httpClientResponse.code()));
                            throw new InvalidHttpResponseSoapException(httpClientResponse.code(), body);
                        });
                }
                return FlowUtils.toByteArrayFuture(httpClientResponse.body())
                    .handle((body, error) -> {
                        if (error != null) {
                            telemetry.failure(new ProcessException(error));
                            throw new SoapException(error);
                        }
                        try {
                            if (httpClientResponse.code() == 200) {
                                var contentType = httpClientResponse.headers().getFirst("content-type");
                                final SoapResult.Success result;
                                if (contentType != null && contentType.toLowerCase().startsWith("multipart")) {
                                    result = readMultipart(contentType, new ByteArrayInputStream(body));
                                } else {
                                    var responseEnvelope = this.xmlTools.unmarshal(new ByteArrayInputStream(body));
                                    result = new SoapResult.Success(responseEnvelope.getBody().getAny().get(0), body);
                                }
                                telemetry.success(result);
                                return result;
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
    }

    private SoapException parseInvalidHttpCodeResponse(HttpClientResponse httpClientResponse) {
        try (var body = httpClientResponse.body();
             var is = body.asInputStream()) {
            var bodyAsBytes = is.readAllBytes();
            return new InvalidHttpResponseSoapException(httpClientResponse.code(), bodyAsBytes);
        } catch (IOException e) {
            return new SoapException(e);
        }
    }

    private SoapResult.Success readSuccess(InputStream body) throws IOException {
        var bodyAsBytes = body.readAllBytes();
        try (var bi = new ByteArrayInputStream(bodyAsBytes)) {
            var responseEnvelope = this.xmlTools.unmarshal(bi);
            return new SoapResult.Success(responseEnvelope.getBody().getAny().get(0), bodyAsBytes);
        }
    }

    private SoapResult.Success readMultipart(String contentType, InputStream body) throws IOException {
        var multipartMeta = MultipartParser.parseMeta(contentType);
        var bodyAsBytes = body.readAllBytes();
        var parts = MultipartParser.parse(bodyAsBytes, multipartMeta.boundary());
        var xmlPartId = multipartMeta.start();
        var responseEnvelope = (SoapEnvelope) this.xmlTools.unmarshal(parts, xmlPartId);
        var responseBody = responseEnvelope.getBody().getAny().get(0);
        return new SoapResult.Success(responseBody, bodyAsBytes);
    }

    private SoapResult.Failure readFailure(InputStream body) throws IOException {
        var bodyAsBytes = body.readAllBytes();
        try (var bi = new ByteArrayInputStream(bodyAsBytes)) {
            var responseEnvelope = this.xmlTools.unmarshal(bi);
            var fault = (SoapFault) responseEnvelope.getBody().getAny().get(0);
            var faultMessage = fault.getFaultcode().toString() + " " + fault.getFaultstring();
            return new SoapResult.Failure(fault, faultMessage, bodyAsBytes);
        }
    }
}
