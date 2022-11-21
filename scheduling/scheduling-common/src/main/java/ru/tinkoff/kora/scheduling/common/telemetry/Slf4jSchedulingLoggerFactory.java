package ru.tinkoff.kora.scheduling.common.telemetry;

import org.slf4j.LoggerFactory;

public final class Slf4jSchedulingLoggerFactory implements SchedulingLoggerFactory {
    @Override
    public SchedulingLogger get(Class<?> jobClass, String jobMethod) {
        var log = LoggerFactory.getLogger(jobClass.getCanonicalName() + "." + jobMethod);
        return new Slf4jSchedulingLogger(log, jobClass.getCanonicalName(), jobMethod);
    }
}
