package io.koraframework.soap.client.common.telemetry.impl;

import io.koraframework.soap.client.common.SoapResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;

public class DefaultSoapClientLoggerFactory {

    public static final DefaultSoapClientLoggerFactory INSTANCE = new DefaultSoapClientLoggerFactory();

    public DefaultSoapClientLogger create(DefaultSoapClientTelemetry.TelemetryContext context) {
        var requestLog = LoggerFactory.getLogger(context.clientCanonicalName() + ".request");
        var responseLog = LoggerFactory.getLogger(context.clientCanonicalName() + ".response");
        return new DefaultSoapClientLogger(requestLog, responseLog, context);
    }

    public static class DefaultSoapClientLogger {

        protected final Logger requestLog;
        protected final Logger responseLog;
        protected final DefaultSoapClientTelemetry.TelemetryContext context;

        public DefaultSoapClientLogger(Logger requestLog,
                                       Logger responseLog,
                                       DefaultSoapClientTelemetry.TelemetryContext context) {
            this.requestLog = requestLog;
            this.responseLog = responseLog;
            this.context = context;
        }

        public void logRequest(byte[] requestXml) {
            if (!requestLog.isInfoEnabled()) {
                return;
            }

            var level = requestLog.isTraceEnabled()
                ? Level.TRACE
                : requestLog.isDebugEnabled()
                ? Level.DEBUG
                : Level.INFO;

            var event = requestLog.atLevel(level)
                .addKeyValue("clientConfigPath", this.context.clientConfigPath())
                .addKeyValue("soapMethod", this.context.descriptor().method())
                .addKeyValue("soapService", this.context.descriptor().service());
            if (requestLog.isTraceEnabled()) {
                event.addKeyValue("soapRequestBody", prepareRequestBodyForLog(requestXml));
            }
            event.log("SoapService requesting");
        }

        public void logResponse(byte[] resultXml) {
            if (!responseLog.isInfoEnabled()) {
                return;
            }

            var level = responseLog.isTraceEnabled()
                ? Level.TRACE
                : responseLog.isDebugEnabled()
                ? Level.DEBUG
                : Level.INFO;

            var event = responseLog.atLevel(level)
                .addKeyValue("clientConfigPath", this.context.clientConfigPath())
                .addKeyValue("soapMethod", this.context.descriptor().method())
                .addKeyValue("soapService", this.context.descriptor().service())
                .addKeyValue("soapStatus", "success");
            if (responseLog.isTraceEnabled()) {
                event.addKeyValue("soapResponseBody", prepareResponseBodyForLog(resultXml));
            }
            event.log("SoapService received response");
        }

        public void logFailure(SoapResult.Failure result) {
            if (!responseLog.isInfoEnabled()) {
                return;
            }

            responseLog.atInfo()
                .addKeyValue("clientConfigPath", this.context.clientConfigPath())
                .addKeyValue("soapMethod", this.context.descriptor().method())
                .addKeyValue("soapService", this.context.descriptor().service())
                .addKeyValue("soapStatus", "failure")
                .addKeyValue("soapFaultCode", result.fault().getFaultcode().toString())
                .addKeyValue("soapFaultActor", result.fault().getFaultactor())
                .log("SoapService received 'failure'");
        }

        public void logError(Throwable e) {
            if (!responseLog.isInfoEnabled()) {
                return;
            }

            responseLog.atInfo()
                .addKeyValue("clientConfigPath", this.context.clientConfigPath())
                .addKeyValue("soapMethod", this.context.descriptor().method())
                .addKeyValue("soapService", this.context.descriptor().service())
                .addKeyValue("soapStatus", "failure")
                .addKeyValue("exceptionType", e.getClass().getCanonicalName())
                .log("SoapService received 'failure'");
        }

        protected String prepareRequestBodyForLog(byte[] requestXml) {
            return new String(requestXml, StandardCharsets.UTF_8);
        }

        protected String prepareResponseBodyForLog(byte[] xml) {
            return new String(xml, StandardCharsets.UTF_8);
        }
    }
}
