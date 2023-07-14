package ru.tinkoff.kora.database.common.telemetry;

public interface DataBaseLoggerFactory {

    DataBaseLogger get(String poolName);

    final class DefaultDataBaseLoggerFactory implements DataBaseLoggerFactory {
        @Override
        public DataBaseLogger get(String poolName) {
            return new DefaultDataBaseLogger(poolName);
        }
    }
}
