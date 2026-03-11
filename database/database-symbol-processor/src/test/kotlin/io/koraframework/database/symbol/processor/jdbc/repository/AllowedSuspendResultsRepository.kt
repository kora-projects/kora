package io.koraframework.database.symbol.processor.jdbc.repository

import io.koraframework.common.Mapping
import io.koraframework.database.common.annotation.Query
import io.koraframework.database.common.annotation.Repository
import io.koraframework.database.jdbc.JdbcRepository
import io.koraframework.database.symbol.processor.entity.TestEntity
import io.koraframework.database.symbol.processor.jdbc.TestEntityJdbcRowMapper
import io.koraframework.database.symbol.processor.jdbc.TestEntityJdbcRowMapperNonFinal

@Repository
interface AllowedSuspendResultsRepository : JdbcRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    suspend fun returnVoid()

    @Query("SELECT test")
    suspend fun returnPrimitive(): Int

    @Query("SELECT test")
    suspend fun returnObject(): Int?

    @Query("SELECT test")
    suspend fun returnNullableObject(): Int?

    @Query("SELECT test")
    @Mapping(TestEntityJdbcRowMapper::class)
    suspend fun returnObjectWithRowMapper(): TestEntity

    @Query("SELECT test")
    @Mapping(TestEntityJdbcRowMapperNonFinal::class)
    suspend fun returnObjectWithRowMapperNonFinal(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityJdbcRowMapper::class)
    suspend fun returnOptionalWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityJdbcRowMapper::class)
    suspend fun returnListWithRowMapper(): List<TestEntity>

}
