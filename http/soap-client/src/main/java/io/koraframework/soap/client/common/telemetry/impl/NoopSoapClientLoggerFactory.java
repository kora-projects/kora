package io.koraframework.soap.client.common.telemetry.impl;

import io.koraframework.soap.client.common.SoapResult;
import org.slf4j.helpers.NOPLogger;

public final class NoopSoapClientLoggerFactory extends DefaultSoapClientLoggerFactory {

    public static final NoopSoapClientLoggerFactory INSTANCE = new NoopSoapClientLoggerFactory();

    private NoopSoapClientLoggerFactory() {}

    @Override
    public DefaultSoapClientLogger create(DefaultSoapClientTelemetry.TelemetryContext context) {
        return NoopSoapClientLogger.INSTANCE;
    }

    public static final class NoopSoapClientLogger extends DefaultSoapClientLogger {

        public static final NoopSoapClientLogger INSTANCE = new NoopSoapClientLogger();

        private NoopSoapClientLogger() {
            super(NOPLogger.NOP_LOGGER, NOPLogger.NOP_LOGGER, DefaultSoapClientTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logRequest(byte[] requestXml) {

        }

        @Override
        public void logResponse(byte[] resultXml) {

        }

        @Override
        public void logFailure(SoapResult.Failure result) {

        }

        @Override
        public void logError(Throwable e) {

        }
    }
}
