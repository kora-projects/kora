package io.koraframework.database.common.annotation.processor.cassandra.repository;

import io.koraframework.common.Mapping;
import io.koraframework.database.cassandra.CassandraRepository;
import io.koraframework.database.common.annotation.Query;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.common.annotation.processor.cassandra.CassandraEntity;
import io.koraframework.database.common.annotation.processor.entity.TestEntityRecord;
import org.jspecify.annotations.Nullable;

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
