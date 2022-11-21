package ru.tinkoff.kora.database.common.annotation.processor.cassandra.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.cassandra.CassandraRepository;
import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.common.annotation.processor.cassandra.CassandraEntity;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;

import java.util.List;

@Repository
public interface AllowedReactiveResultsRepository extends CassandraRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    Mono<Void> returnVoid();

    @Query("SELECT test")
    Mono<Integer> returnObject();

    @Query("SELECT test")
    @Mapping(CassandraEntity.TestEntityCassandraRowMapper.class)
    Mono<TestEntityRecord> returnObjectWithRowMapper();

    @Query("SELECT test")
    @Mapping(CassandraEntity.TestEntityCassandraRowMapperNonFinal.class)
    Mono<TestEntityRecord> returnObjectWithRowMapperNonFinal();

    @Query("SELECT test")
    @Mapping(CassandraEntity.TestEntityCassandraRowMapper.class)
    Mono<List<TestEntityRecord>> returnListWithRowMapper();

    @Query("SELECT test")
    Flux<Integer> returnFlux();

    @Query("SELECT test")
    @Mapping(CassandraEntity.TestEntityCassandraRowMapper.class)
    Flux<TestEntityRecord> returnFluxWithRowMapper();

    @Query("SELECT test")
    @Mapping(CassandraEntity.TestEntityCassandraRowMapperNonFinal.class)
    Flux<TestEntityRecord> returnFluxWithRowMapperNonFinal();
}
