package io.koraframework.soap.client.common;

import io.koraframework.soap.client.common.exception.SoapInvalidHttpResponseException;
import io.koraframework.soap.client.common.exception.SoapException;
import io.koraframework.soap.client.common.util.MultipartParserUtils;
import io.opentelemetry.context.Context;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.exception.HttpClientException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.koraframework.soap.client.common.envelope.SoapFault;
import io.koraframework.soap.client.common.telemetry.SoapClientTelemetry;
import io.koraframework.soap.client.common.telemetry.SoapClientTelemetryFactory;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

public class SoapRequestExecutor {

    private static final Pattern CHARSET_PATTERN = Pattern.compile(";\\s*charset\\s*=\\s*\"?([^\";\\s]+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final SoapEnvelopeMapper soapMapper;
    private final String url;
    private final String soapAction;
    private final SoapClientTelemetry telemetry;
    private final Duration timeout;

    public SoapRequestExecutor(HttpClient httpClient,
                               SoapClientTelemetryFactory telemetryFactory,
                               SoapEnvelopeMapper soapMapper,
                               SoapServiceConfig config,
                               String configPath,
                               SoapMethodDescriptor methodDescriptor) {
        this.httpClient = httpClient;
        this.soapMapper = soapMapper;
        this.url = config.url();
        this.timeout = config.timeout();
        this.soapAction = methodDescriptor.soapAction();
        this.telemetry = telemetryFactory.get(configPath, methodDescriptor.serviceClass(), config.telemetry(), methodDescriptor, url);
    }

    public SoapResult call(SoapEnvelope requestEnvelope) throws SoapException {
        var observation = this.telemetry.observe(requestEnvelope);
        return ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> {
                try {
                    observation.observeRequest(requestEnvelope);
                    var requestXml = this.soapMapper.marshal(requestEnvelope);
                    observation.observeRequestXml(requestXml);
                    var httpClientRequest = HttpClientRequest.post(this.url)
                        .body(HttpBody.of("text/xml; charset=utf-8", requestXml))
                        .requestTimeout((int) timeout.toMillis());
                    if (this.soapAction != null) {
                        httpClientRequest.header("SOAPAction", this.soapAction);
                    }
                    try (var httpClientResponse = this.httpClient.execute(httpClientRequest.build());
                         var body = httpClientResponse.body();
                         var is = body.asInputStream()) {
                        observation.observeHttpResponse(httpClientResponse);

                        if (httpClientResponse.code() / 100 != 2 && httpClientResponse.code() != 500) {
                            try {
                                var bodyAsBytes = is.readAllBytes();
                                observation.observeResponseBody(bodyAsBytes);
                                throw new SoapInvalidHttpResponseException(httpClientResponse.code(), bodyAsBytes);
                            } catch (IOException e) {
                                var ex = new SoapInvalidHttpResponseException(httpClientResponse.code(), new byte[0]);
                                ex.addSuppressed(e);
                                throw ex;
                            }
                        }
                        var contentType = httpClientResponse.headers().getFirst("content-type");
                        if (httpClientResponse.code() == 500) {
                            var bytes = is.readAllBytes();
                            var result = readFailure(new ByteArrayInputStream(bytes), charset(contentType));
                            observation.observeResponseBody(bytes);
                            observation.observeFailure(result);
                            return result;
                        }
                        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart")) {
                            var result = readMultipart(contentType, is);
                            observation.observeResponseBody(result.xmlPart().getContentArray());
                            observation.observeResult(result.result.body());
                            return result.result;
                        } else {
                            var xml = is.readAllBytes();
                            observation.observeResponseBody(xml);
                            // one-way operations are answered with an empty 2xx (usually 202 Accepted)
                            var success = xml.length == 0
                                ? new SoapResult.Success(null)
                                : readSuccess(new ByteArrayInputStream(xml), charset(contentType));
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

    private SoapResult.Success readSuccess(InputStream body, @Nullable Charset charset) throws IOException {
        var bodyAsBytes = body.readAllBytes();
        try (var bi = new ByteArrayInputStream(bodyAsBytes)) {
            var responseEnvelope = this.soapMapper.unmarshal(bi, charset);
            return new SoapResult.Success(responseEnvelope.getBody().getAny().get(0));
        }
    }

    private record ParseMultipartResult(SoapResult.Success result, MultipartParserUtils.Part xmlPart) {}

    private ParseMultipartResult readMultipart(String contentType, InputStream body) throws IOException {
        var multipartMeta = MultipartParserUtils.parseMeta(contentType);
        var bodyAsBytes = body.readAllBytes();
        var parts = MultipartParserUtils.parse(bodyAsBytes, multipartMeta.boundary());
        var xmlPartId = multipartMeta.start();
        var responseEnvelope = (SoapEnvelope) this.soapMapper.unmarshal(parts, xmlPartId);
        var responseBody = responseEnvelope.getBody().getAny().get(0);
        return new ParseMultipartResult(new SoapResult.Success(responseBody), parts.get(xmlPartId));
    }

    private SoapResult.Failure readFailure(InputStream body, @Nullable Charset charset) throws IOException {
        var responseEnvelope = this.soapMapper.unmarshal(body, charset);
        var fault = (SoapFault) responseEnvelope.getBody().getAny().get(0);
        var faultMessage = fault.getFaultcode().toString() + " " + fault.getFaultstring();
        return new SoapResult.Failure(fault, faultMessage);
    }

    @Nullable
    private static Charset charset(@Nullable String contentType) {
        if (contentType == null) {
            return null;
        }
        var matcher = CHARSET_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Charset.forName(matcher.group(1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
