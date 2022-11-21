package ru.tinkoff.kora.database.common.annotation.processor.cassandra.repository;

import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.cassandra.CassandraRepository;
import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.common.annotation.processor.cassandra.CassandraEntity;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

@Repository
public interface AllowedResultsRepository extends CassandraRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    void returnVoid();

    @Query("SELECT test")
    int returnPrimitive();

    @Query("SELECT test")
    Integer returnObject();

    @Query("SELECT test")
    @Nullable
    Integer returnNullableObject();

    @Query("SELECT test")
    @Mapping(CassandraEntity.TestEntityCassandraRowMapper.class)
    TestEntityRecord returnObjectWithRowMapper();

    @Query("SELECT test")
    @Mapping(CassandraEntity.TestEntityCassandraRowMapperNonFinal.class)
    @Nullable
    TestEntityRecord returnObjectWithRowMapperNonFinal();

    @Query("SELECT test")
    @Mapping(CassandraEntity.TestEntityCassandraRowMapper.class)
    Optional<TestEntityRecord> returnOptionalWithRowMapper();

    @Query("SELECT test")
    @Mapping(CassandraEntity.TestEntityCassandraRowMapper.class)
    List<TestEntityRecord> returnListWithRowMapper();
}
