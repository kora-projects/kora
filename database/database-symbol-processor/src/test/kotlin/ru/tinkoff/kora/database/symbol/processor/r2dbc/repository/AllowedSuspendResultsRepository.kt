package ru.tinkoff.kora.database.symbol.processor.r2dbc.repository

import kotlinx.coroutines.flow.Flow
import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.r2dbc.R2dbcRepository
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.symbol.processor.r2dbc.TestEntityR2dbcRowMapper
import ru.tinkoff.kora.database.symbol.processor.r2dbc.TestEntityR2dbcRowMapperNonFinal

@Repository
interface AllowedSuspendResultsRepository : R2dbcRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    suspend fun returnVoid()

    @Query("SELECT test")
    suspend fun returnPrimitive(): Int

    @Query("SELECT test")
    suspend fun returnObject(): Int?

    @Query("SELECT test")
    suspend fun returnNullableObject(): Int?

    @Query("SELECT test")
    @Mapping(TestEntityR2dbcRowMapper::class)
    suspend fun returnObjectWithRowMapper(): TestEntity

    @Query("SELECT test")
    @Mapping(TestEntityR2dbcRowMapperNonFinal::class)
    suspend fun returnObjectWithRowMapperNonFinal(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityR2dbcRowMapper::class)
    suspend fun returnOptionalWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityR2dbcRowMapper::class)
    suspend fun returnListWithRowMapper(): List<TestEntity>

    @Query("SELECT test")
    fun returnObjectFlow(): Flow<Int>

    @Query("SELECT test")
    @Mapping(TestEntityR2dbcRowMapper::class)
    fun returnObjectFlowWithRowMapper(): Flow<TestEntity>

    @Query("SELECT test")
    @Mapping(TestEntityR2dbcRowMapperNonFinal::class)
    fun returnObjectFlowWithRowMapperNonFinal(): Flow<TestEntity>
}
