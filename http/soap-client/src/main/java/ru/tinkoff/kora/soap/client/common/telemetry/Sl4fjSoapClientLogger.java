package ru.tinkoff.kora.soap.client.common.telemetry;

import org.slf4j.Logger;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure;

import java.nio.charset.StandardCharsets;

public class Sl4fjSoapClientLogger implements SoapClientLogger {

    private final String serviceName;
    private final String soapMethod;

    private final SoapClientLoggerBodyMapper mapper;
    private final Logger requestLog;
    private final Logger responseLog;

    public Sl4fjSoapClientLogger(SoapClientLoggerBodyMapper mapper, Logger requestLog, Logger responseLog, String serviceName, String soapMethod) {
        this.mapper = mapper;
        this.requestLog = requestLog;
        this.responseLog = responseLog;

        this.serviceName = serviceName;
        this.soapMethod = soapMethod;
    }

    @Override
    public void logRequest(SoapEnvelope requestEnvelope, byte[] requestEnvelopeAsBytes) {
        var marker = StructuredArgument.marker("soapRequest", gen -> {
            gen.writeStartObject();
            gen.writeStringField("soapMethod", soapMethod);
            gen.writeStringField("soapService", serviceName);
            gen.writeEndObject();
        });

        if (requestLog.isTraceEnabled()) {
            requestLog.trace(marker, "SoapService requesting method: {}\n{}", soapMethod,
                mapper.mapRequest(requestEnvelopeAsBytes));
        } else {
            requestLog.info(marker, "SoapService requesting method: {}", soapMethod);
        }
    }

    @Override
    public void logSuccess(SoapResult.Success result) {
        var marker = StructuredArgument.marker("soapResponse", gen -> {
            gen.writeStartObject();
            gen.writeStringField("soapMethod", soapMethod);
            gen.writeStringField("soapService", serviceName);
            gen.writeStringField("soapStatus", "success");
            gen.writeEndObject();
        });

        if (responseLog.isTraceEnabled()) {
            responseLog.trace(marker, "SoapService received 'success' for method: {}\n{}", soapMethod,
                mapper.mapResponseSuccess(result.bodyAsBytes()));
        } else {
            responseLog.info(marker, "SoapService received 'success' for method: {}", soapMethod);
        }
    }

    @Override
    public void logFailure(SoapClientFailure failure) {
        var marker = StructuredArgument.marker("soapResponse", gen -> {
            gen.writeStartObject();
            gen.writeStringField("soapMethod", soapMethod);
            gen.writeStringField("soapService", serviceName);
            gen.writeStringField("soapStatus", "success");

            if (failure instanceof SoapClientFailure.InvalidHttpCode ie) {
                gen.writeNumberField("soapHttpCode", ie.code());
            } else if (failure instanceof SoapClientFailure.InternalServerError se) {
                gen.writeStringField("soapFaultCode", se.result().fault().getFaultcode().toString());
                gen.writeStringField("soapFaultActor", se.result().fault().getFaultactor());
            } else if (failure instanceof SoapClientFailure.ProcessException pe) {
                gen.writeStringField("exceptionType", pe.throwable().getClass().getCanonicalName());
            }

            gen.writeEndObject();
        });

        if (failure instanceof SoapClientFailure.InternalServerError se) {
            if (responseLog.isTraceEnabled()) {
                responseLog.trace(marker, "SoapService received 'failure' for method '{}' and message: {}\n{}",
                    soapMethod, se.result().faultMessage(), mapper.mapResponseFailure(se.result().bodyAsBytes()));
            } else {
                responseLog.info(marker, "SoapService received 'failure' for method '{}' and message: {}",
                    soapMethod, se.result().faultMessage());
            }
        } else if (failure instanceof SoapClientFailure.ProcessException pe) {
            if (responseLog.isTraceEnabled()) {
                responseLog.trace(marker, "SoapService received 'failure' for method '{}' and cause:",
                    soapMethod, pe.throwable());
            } else {
                responseLog.info(marker, "SoapService received 'failure' for method '{}' and cause: {}",
                    soapMethod, pe.throwable().getMessage());
            }
        } else {
            responseLog.info(marker, "SoapService received 'failure' for method: {}", soapMethod);
        }
    }
}
