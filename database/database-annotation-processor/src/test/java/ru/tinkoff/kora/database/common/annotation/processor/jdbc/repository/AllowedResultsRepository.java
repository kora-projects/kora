package ru.tinkoff.kora.database.common.annotation.processor.jdbc.repository;

import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.UpdateCount;
import ru.tinkoff.kora.database.common.annotation.Batch;
import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.JdbcEntity;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

@Repository
public interface AllowedResultsRepository extends JdbcRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    void returnVoid();

    @Query("INSERT INTO test(test) VALUES (10)")
    UpdateCount returnUpdateCount();

    @Query("SELECT test")
    int returnPrimitive();

    @Query("INSERT INTO test(test) VALUES (:someint)")
    int[] returnBatchUpdate(@Batch List<Integer> someint);

    @Query("INSERT INTO test(test) VALUES (:someint)")
    void returnBatchVoid(@Batch List<Integer> someint);

    @Query("SELECT test")
    Integer returnObject();

    @Query("SELECT test")
    @Nullable
    Integer returnNullableObject();

    @Query("SELECT test")
    @Mapping(JdbcEntity.TestEntityJdbcRowMapper.class)
    TestEntityRecord returnObjectWithRowMapper();

    @Query("SELECT test")
    @Mapping(JdbcEntity.TestEntityJdbcRowMapperNonFinal.class)
    @Nullable
    TestEntityRecord returnObjectWithRowMapperNonFinal();

    // row mapper returns _not_ optional
    @Query("SELECT test")
    @Mapping(JdbcEntity.TestEntityJdbcRowMapper.class)
    Optional<TestEntityRecord> returnOptionalWithRowMapper();

    // result set mapper should handle optional wrapping
    @Query("SELECT test")
    @Mapping(JdbcEntity.OptionalMappedEntityResultSetMapper.class)
    Optional<TestEntityRecord> returnOptionalWithResultSetMapper();

    // row mapper returns _not_ list
    @Query("SELECT test")
    @Mapping(JdbcEntity.TestEntityJdbcRowMapper.class)
    List<TestEntityRecord> returnListWithRowMapper();

    // result set mapper should handle list wrapping
    @Query("SELECT test")
    @Mapping(JdbcEntity.ListMappedEntityResultSetMapper.class)
    List<TestEntityRecord> returnListWithResultSetMapper();
}
