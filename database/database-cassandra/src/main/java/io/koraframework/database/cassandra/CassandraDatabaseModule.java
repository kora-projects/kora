package io.koraframework.database.cassandra;

import io.koraframework.common.annotation.FactoryModule;

public interface CassandraDatabaseModule extends CassandraMapperModule {

    @FactoryModule
    default CassandraDatabaseFactoryModule cassandraDatabase() {
        return new CassandraDatabaseFactoryModule("cassandra");
    }
}
