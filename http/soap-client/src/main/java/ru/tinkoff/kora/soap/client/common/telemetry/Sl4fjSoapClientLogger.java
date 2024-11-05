package ru.tinkoff.kora.soap.client.common.telemetry;

import org.slf4j.Logger;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure;

public class Sl4fjSoapClientLogger implements SoapClientLogger {

    private final String serviceName;
    private final String soapMethod;
    private final String url;

    private final Logger requestLog;
    private final Logger responseLog;

    public Sl4fjSoapClientLogger(Logger requestLog, Logger responseLog, String serviceName, String soapMethod, String url) {
        this.requestLog = requestLog;
        this.responseLog = responseLog;

        this.serviceName = serviceName;
        this.soapMethod = soapMethod;
        this.url = url;
    }

    @Override
    public void logRequest(SoapEnvelope requestEnvelope) {
        var marker = StructuredArgument.marker("soapRequest", gen -> {
            gen.writeStartObject();
            gen.writeStringField("soapMethod", soapMethod);
            gen.writeStringField("soapService", serviceName);
            gen.writeEndObject();
        });

        if (requestLog.isTraceEnabled()) {
            if (!requestEnvelope.getHeader().getAny().isEmpty()) {
                requestLog.trace(marker, "SoapService requesting method: {}\n{}", soapMethod,
                    requestEnvelope.getBody().getAny());
            } else {
                requestLog.trace(marker, "SoapService requesting method: {}\n{}\n{}", soapMethod,
                    requestEnvelope.getHeader().getAny(),
                    requestEnvelope.getBody().getAny());
            }
        } else if (requestLog.isDebugEnabled() && !requestEnvelope.getHeader().getAny().isEmpty()) {
            requestLog.debug(marker, "SoapService requesting method: {}\n{}", soapMethod, requestEnvelope.getHeader().getAny());
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
            responseLog.trace(marker, "SoapService received 'success' for method: {}\n{}", soapMethod, result.body());
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
            responseLog.info(marker, "SoapService received 'failure' for method '{}' and message: {}",
                soapMethod, se.result().faultMessage());
        } else if (failure instanceof SoapClientFailure.ProcessException pe) {
            if (responseLog.isTraceEnabled()) {
                responseLog.info(marker, "SoapService received 'failure' for method '{}' and cause:", soapMethod, pe.throwable());
            } else {
                responseLog.info(marker, "SoapService received 'failure' for method '{}' and cause: {}", soapMethod, pe.throwable().getMessage());
            }
        } else {
            responseLog.info(marker, "SoapService received 'failure' for method: {}", soapMethod);
        }
    }
}
