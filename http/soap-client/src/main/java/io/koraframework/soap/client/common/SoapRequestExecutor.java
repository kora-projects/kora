package io.koraframework.soap.client.common;

import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.koraframework.http.client.common.*;
import io.koraframework.http.client.common.exception.*;
import io.koraframework.http.client.common.request.*;
import io.koraframework.http.common.body.*;
import io.koraframework.soap.client.common.envelope.*;
import io.koraframework.soap.client.common.exception.*;
import io.koraframework.soap.client.common.telemetry.*;
import io.koraframework.soap.client.common.util.*;
import io.opentelemetry.context.*;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.function.*;

public class SoapRequestExecutor {

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

        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(observation.span()));

        try {
            return KoraContext.runWith(updatedContext, new SoapCallRunner(this, requestEnvelope, observation));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SoapException se) {
                throw se;
            }
            throw e;
        }
    }

    private SoapResult.Success readSuccess(InputStream body) throws IOException {
        var bodyAsBytes = body.readAllBytes();
        try (var bi = new ByteArrayInputStream(bodyAsBytes)) {
            var responseEnvelope = this.soapMapper.unmarshal(bi);
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

    private SoapResult.Failure readFailure(InputStream body) throws IOException {
        var responseEnvelope = this.soapMapper.unmarshal(body);
        var fault = (SoapFault) responseEnvelope.getBody().getAny().get(0);
        var faultMessage = fault.getFaultcode().toString() + " " + fault.getFaultstring();
        return new SoapResult.Failure(fault, faultMessage);
    }

    private static final class SoapCallRunner implements Supplier<SoapResult> {
        private final SoapRequestExecutor executor;
        private final SoapEnvelope requestEnvelope;
        private final SoapClientObservation observation;

        public SoapCallRunner(SoapRequestExecutor executor,
                              SoapEnvelope requestEnvelope,
                              SoapClientObservation observation) {
            this.executor = executor;
            this.requestEnvelope = requestEnvelope;
            this.observation = observation;
        }

        @Override
        public SoapResult get() {
            try {
                observation.observeRequest(requestEnvelope);
                var requestXml = executor.soapMapper.marshal(requestEnvelope);
                observation.observeRequestXml(requestXml);
                var httpClientRequest = HttpClientRequest.post(executor.url)
                    .body(HttpBody.of("text/xml", requestXml))
                    .requestTimeout((int) executor.timeout.toMillis());
                if (executor.soapAction != null) {
                    httpClientRequest.header("SOAPAction", executor.soapAction);
                }
                try (var httpClientResponse = executor.httpClient.execute(httpClientRequest.build());
                     var body = httpClientResponse.body();
                     var is = body.asInputStream()) {
                    observation.observeHttpResponse(httpClientResponse);

                    if (httpClientResponse.code() != 200 && httpClientResponse.code() != 500) {
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
                    if (httpClientResponse.code() != 200) {
                        var bytes = is.readAllBytes();
                        var result = executor.readFailure(new ByteArrayInputStream(bytes));
                        observation.observeResponseBody(bytes);
                        observation.observeFailure(result);
                        return result;
                    }
                    var contentType = httpClientResponse.headers().getFirst("content-type");
                    if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart")) {
                        var result = executor.readMultipart(contentType, is);
                        observation.observeResponseBody(result.xmlPart().getContentArray());
                        observation.observeResult(result.result.body());
                        return result.result;
                    } else {
                        var xml = is.readAllBytes();
                        observation.observeResponseBody(xml);
                        var success = executor.readSuccess(new ByteArrayInputStream(xml));
                        observation.observeResult(success.body());
                        return success;
                    }
                } catch (IOException | HttpClientException e) {
                    throw new SoapException(e);
                }
            } catch (Throwable t) {
                observation.observeError(t);
                throw new RuntimeException(t);
            } finally {
                observation.end();
            }
        }
    }
}
