package ru.tinkoff.kora.database.common.annotation.processor.jdbc.repository;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.JdbcEntity;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AllowedMonoResultsRepository extends JdbcRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    Mono<Void> returnVoid();

    @Query("SELECT test")
    Mono<Integer> returnObject();

    @Query("SELECT test")
    @Mapping(JdbcEntity.TestEntityJdbcRowMapper.class)
    Mono<TestEntityRecord> returnObjectWithRowMapper();

    @Query("SELECT test")
    @Mapping(JdbcEntity.TestEntityJdbcRowMapperNonFinal.class)
    Mono<TestEntityRecord> returnObjectWithRowMapperNonFinal();

    // row mapper returns _not_ optional
    @Query("SELECT test")
    @Mapping(JdbcEntity.TestEntityJdbcRowMapper.class)
    Mono<Optional<TestEntityRecord>> returnOptionalWithRowMapper();

    // result set mapper should handle optional wrapping
    @Query("SELECT test")
    @Mapping(JdbcEntity.OptionalMappedEntityResultSetMapper.class)
    Mono<Optional<TestEntityRecord>> returnOptionalWithResultSetMapper();

    // row mapper returns _not_ list
    @Query("SELECT test")
    @Mapping(JdbcEntity.TestEntityJdbcRowMapper.class)
    Mono<List<TestEntityRecord>> returnListWithRowMapper();

    // result set mapper should handle list wrapping
    @Query("SELECT test")
    @Mapping(JdbcEntity.ListMappedEntityResultSetMapper.class)
    Mono<List<TestEntityRecord>> returnListWithResultSetMapper();
}
