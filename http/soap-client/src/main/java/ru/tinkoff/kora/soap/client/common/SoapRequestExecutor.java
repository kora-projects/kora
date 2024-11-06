package ru.tinkoff.kora.soap.client.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
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
import java.util.Objects;
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
                try (var body = httpClientResponse.body();
                     var is = body.asInputStream()) {
                    if (is == null) {
                        telemetry.failure(new InvalidHttpCode(httpClientResponse.code()), null);
                        throw new InvalidHttpResponseSoapException(httpClientResponse.code(), new byte[0]);
                    } else {
                        var bodyAsBytes = is.readAllBytes();
                        telemetry.failure(new InvalidHttpCode(httpClientResponse.code()), bodyAsBytes);
                        throw new InvalidHttpResponseSoapException(httpClientResponse.code(), bodyAsBytes);
                    }
                } catch (IOException e) {
                    telemetry.failure(new InvalidHttpCode(httpClientResponse.code()), null);
                    var ex = new InvalidHttpResponseSoapException(httpClientResponse.code(), new byte[0]);
                    ex.addSuppressed(e);
                    throw ex;
                }
            }
            try (var body = httpClientResponse.body();
                 var is = Objects.requireNonNull(body.asInputStream())) {
                if (httpClientResponse.code() != 200) {
                    if (telemetry.logResponseBody()) {
                        var bytes = is.readAllBytes();
                        var result = readFailure(new ByteArrayInputStream(bytes));
                        telemetry.failure(new InternalServerError(result), bytes);
                        return result;
                    } else {
                        var result = readFailure(is);
                        telemetry.failure(new InternalServerError(result), null);
                        return result;
                    }
                }
                var contentType = httpClientResponse.headers().getFirst("content-type");
                if (contentType != null && contentType.toLowerCase().startsWith("multipart")) {
                    var result = readMultipart(contentType, is);
                    if (telemetry.logResponseBody()) {
                        telemetry.success(result.result, result.xmlPart().getContentArray());
                    } else {
                        telemetry.success(result.result, null);
                    }
                    return result.result;
                } else {
                    if (telemetry.logResponseBody()) {
                        var xml = is.readAllBytes();
                        var result = readSuccess(new ByteArrayInputStream(xml));
                        telemetry.success(result, xml);
                        return result;
                    } else {
                        var result = readSuccess(is);
                        telemetry.success(result, null);
                        return result;
                    }
                }
            }
        } catch (IOException | HttpClientException | InterruptedException e) {
            telemetry.failure(new ProcessException(e), null);
            throw new SoapException(e);
        } catch (ExecutionException e) {
            if (e.getCause() != null) {
                telemetry.failure(new ProcessException(e.getCause()), null);
                if (e.getCause() instanceof IOException || e.getCause() instanceof HttpClientException) {
                    throw new SoapException(e.getCause());
                } else if (e.getCause() instanceof RuntimeException re) {
                    throw re;
                }
            } else {
                telemetry.failure(new ProcessException(e), null);
            }
            throw new SoapException(e);
        } catch (Exception e) {
            telemetry.failure(new ProcessException(e), null);
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
                    telemetry.failure(new ProcessException(error), null);
                }
            })
            .thenCompose(httpClientResponse -> {
                if (httpClientResponse.code() != 200 && httpClientResponse.code() != 500) {
                    return FlowUtils.toByteArrayFuture(httpClientResponse.body())
                        .handle((body, sink) -> {
                            telemetry.failure(new InvalidHttpCode(httpClientResponse.code()), body);
                            throw new InvalidHttpResponseSoapException(httpClientResponse.code(), body);
                        });
                }
                return FlowUtils.toByteArrayFuture(httpClientResponse.body())
                    .handle((body, error) -> {
                        if (error != null) {
                            telemetry.failure(new ProcessException(error), body);
                            throw new SoapException(error);
                        }
                        try {
                            if (httpClientResponse.code() == 200) {
                                var contentType = httpClientResponse.headers().getFirst("content-type");
                                if (contentType != null && contentType.toLowerCase().startsWith("multipart")) {
                                    var readMultipartResult = readMultipart(contentType, new ByteArrayInputStream(body));
                                    var result = readMultipartResult.result;
                                    if (telemetry.logResponseBody()) {
                                        telemetry.success(result, readMultipartResult.xmlPart().getContentArray());
                                    } else {
                                        telemetry.success(result, null);
                                    }
                                    return result;
                                } else {
                                    var responseEnvelope = this.xmlTools.unmarshal(new ByteArrayInputStream(body));
                                    var result = new SoapResult.Success(responseEnvelope.getBody().getAny().get(0));
                                    if (telemetry.logResponseBody()) {
                                        telemetry.success(result, body);
                                    } else {
                                        telemetry.success(result, null);
                                    }
                                    return result;
                                }
                            }

                            var result = readFailure(new ByteArrayInputStream(body));
                            telemetry.failure(new InternalServerError(result), body);
                            return result;
                        } catch (IOException e) {
                            telemetry.failure(new ProcessException(e), body);
                            throw new SoapException(e);
                        }
                    });
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
