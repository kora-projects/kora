package io.koraframework.database.symbol.processor.cassandra.repository

import io.koraframework.common.Mapping
import io.koraframework.database.cassandra.CassandraRepository
import io.koraframework.database.common.annotation.Query
import io.koraframework.database.common.annotation.Repository
import io.koraframework.database.symbol.processor.cassandra.TestEntityCassandraRowMapper
import io.koraframework.database.symbol.processor.cassandra.TestEntityCassandraRowMapperNonFinal
import io.koraframework.database.symbol.processor.entity.TestEntity

@Repository
interface AllowedResultsRepository : CassandraRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    fun returnVoid()

    @Query("SELECT test")
    fun returnPrimitive(): Int

    @Query("SELECT test")
    fun returnObject(): Int?

    @Query("SELECT test")
    fun returnNullableObject(): Int?

    @Query("SELECT test")
    @Mapping(TestEntityCassandraRowMapper::class)
    fun returnObjectWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityCassandraRowMapperNonFinal::class)
    fun returnObjectWithRowMapperNonFinal(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityCassandraRowMapper::class)
    fun returnOptionalWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityCassandraRowMapper::class)
    fun returnListWithRowMapper(): List<TestEntity>
}
