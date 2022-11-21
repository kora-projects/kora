package ru.tinkoff.kora.database.common.telemetry;

public interface DataBaseMetricWriterFactory {
    DataBaseMetricWriter get(String poolName);
}
