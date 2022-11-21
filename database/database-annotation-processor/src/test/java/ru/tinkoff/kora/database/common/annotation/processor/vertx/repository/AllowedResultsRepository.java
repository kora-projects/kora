package ru.tinkoff.kora.database.common.annotation.processor.vertx.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.vertx.VertxEntity;
import ru.tinkoff.kora.database.vertx.VertxRepository;

import java.util.List;

@Repository
public interface AllowedResultsRepository extends VertxRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    Mono<Void> returnVoid();

    @Query("SELECT test")
    Mono<TestEntityRecord> returnObject();

    @Query("SELECT test")
    @Mapping(VertxEntity.TestEntityVertxRowMapper.class)
    Mono<TestEntityRecord> returnObjectWithRowMapper();

    @Query("SELECT test")
    @Mapping(VertxEntity.TestEntityVertxRowMapperNonFinal.class)
    Mono<TestEntityRecord> returnObjectWithRowMapperNonFinal();

    @Query("SELECT test")
    @Mapping(VertxEntity.TestEntityVertxRowMapper.class)
    Mono<List<TestEntityRecord>> returnListWithRowMapper();

    @Query("SELECT test")
    @Mapping(VertxEntity.TestEntityVertxRowMapperNonFinal.class)
    Mono<List<TestEntityRecord>> returnListWithResultSetMapper();

    @Query("SELECT test")
    @Mapping(VertxEntity.TestEntityVertxRowMapper.class)
    Flux<TestEntityRecord> returnObjectFluxWithRowMapper();

    @Query("SELECT test")
    @Mapping(VertxEntity.TestEntityVertxRowMapperNonFinal.class)
    Flux<TestEntityRecord> returnObjectFluxWithRowMapperNonFinal();


    @Query("INSERT INTO test(test) VALUES ('test')")
    void returnVoidBlocking();

    @Query("SELECT test")
    TestEntityRecord returnObjectBlocking();

    @Query("SELECT test")
    @Mapping(VertxEntity.TestEntityVertxRowMapper.class)
    TestEntityRecord returnObjectWithRowMapperBlocking();

    @Query("SELECT test")
    @Mapping(VertxEntity.TestEntityVertxRowMapperNonFinal.class)
    TestEntityRecord returnObjectWithRowMapperNonFinalBlocking();

    @Query("SELECT test")
    @Mapping(VertxEntity.TestEntityVertxRowMapper.class)
    List<TestEntityRecord> returnListWithRowMapperBlocking();

    @Query("SELECT test")
    @Mapping(VertxEntity.TestEntityVertxRowMapperNonFinal.class)
    List<TestEntityRecord> returnListWithResultSetMapperBlocking();


}
