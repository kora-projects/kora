package io.koraframework.redis.jedis.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import redis.clients.jedis.CommandObject;

public final class NoopJedisMetricsFactory extends DefaultJedisMetricsFactory {

    public static final NoopJedisMetricsFactory INSTANCE = new NoopJedisMetricsFactory();

    @Override
    public DefaultJedisMetrics create(DefaultJedisTelemetry.TelemetryContext context) {
        return NoopJedisMetrics.INSTANCE;
    }

    private static class NoopJedisMetrics extends DefaultJedisMetrics {

        private static final NoopJedisMetrics INSTANCE = new NoopJedisMetrics(null);

        public NoopJedisMetrics(DefaultJedisTelemetry.TelemetryContext context) {
            super(context);
        }

        public void recordFailure(CommandObject<?> command,
                                  String commandName,
                                  Throwable exception,
                                  long processingTimeNanos) {
            // do nothing
        }

        public void recordSuccess(CommandObject<?> command,
                                  String commandName,
                                  Object result,
                                  long processingTimeNanos) {
            // do nothing
        }
    }
}
