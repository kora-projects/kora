package ru.tinkoff.kora.database.common.annotation.processor.r2dbc.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.r2dbc.R2dbcEntity;
import ru.tinkoff.kora.database.r2dbc.R2dbcRepository;

import java.util.List;

@Repository
public interface AllowedResultsRepository extends R2dbcRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    Mono<Void> returnVoid();

    @Query("SELECT test")
    Mono<TestEntityRecord> returnObject();

    @Query("SELECT test")
    @Mapping(R2dbcEntity.TestEntityR2dbcRowMapper.class)
    Mono<TestEntityRecord> returnObjectWithRowMapper();

    @Query("SELECT test")
    @Mapping(R2dbcEntity.TestEntityR2dbcRowMapperNonFinal.class)
    Mono<TestEntityRecord> returnObjectWithRowMapperNonFinal();

    // row mapper returns _not_ list
    @Query("SELECT test")
    @Mapping(R2dbcEntity.TestEntityR2dbcRowMapper.class)
    Mono<List<TestEntityRecord>> returnListWithRowMapper();

    // result set mapper should handle list wrapping
    @Query("SELECT test")
    @Mapping(R2dbcEntity.TestEntityR2dbcRowMapperNonFinal.class)
    Mono<List<TestEntityRecord>> returnListWithResultSetMapper();

    @Query("SELECT test")
    Flux<TestEntityRecord> returnObjectFlux();

    @Query("SELECT test")
    @Mapping(R2dbcEntity.TestEntityR2dbcRowMapper.class)
    Flux<TestEntityRecord> returnObjectFluxWithRowMapper();

    @Query("SELECT test")
    @Mapping(R2dbcEntity.TestEntityR2dbcRowMapperNonFinal.class)
    Flux<TestEntityRecord> returnObjectFluxWithRowMapperNonFinal();
}
