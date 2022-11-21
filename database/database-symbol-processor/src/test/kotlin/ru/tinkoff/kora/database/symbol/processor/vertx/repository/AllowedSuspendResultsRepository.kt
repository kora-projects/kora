package ru.tinkoff.kora.database.symbol.processor.vertx.repository

import kotlinx.coroutines.flow.Flow
import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.symbol.processor.vertx.TestEntityVertxRowMapper
import ru.tinkoff.kora.database.symbol.processor.vertx.TestEntityVertxRowMapperNonFinal
import ru.tinkoff.kora.database.symbol.processor.vertx.TestEntityVertxRowMapperNonNull
import ru.tinkoff.kora.database.vertx.VertxRepository

@Repository
interface AllowedSuspendResultsRepository : VertxRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    suspend fun returnVoid()

    @Query("SELECT test")
    suspend fun returnPrimitive(): Int

    @Query("SELECT test")
    suspend fun returnObject(): Int?

    @Query("SELECT test")
    suspend fun returnNullableObject(): Int?

    @Query("SELECT test")
    @Mapping(TestEntityVertxRowMapperNonNull::class)
    suspend fun returnObjectWithRowMapper(): TestEntity

    @Query("SELECT test")
    @Mapping(TestEntityVertxRowMapperNonFinal::class)
    suspend fun returnObjectWithRowMapperNonFinal(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityVertxRowMapper::class)
    suspend fun returnOptionalWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityVertxRowMapperNonNull::class)
    suspend fun returnListWithRowMapper(): List<TestEntity>

    @Query("SELECT test")
    fun returnObjectFlow(): Flow<Int>

    @Query("SELECT test")
    @Mapping(TestEntityVertxRowMapperNonNull::class)
    fun returnObjectFlowWithRowMapper(): Flow<TestEntity>
}
