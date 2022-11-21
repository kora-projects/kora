package ru.tinkoff.kora.database.symbol.processor.vertx.repository

import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.symbol.processor.vertx.TestEntityVertxRowMapper
import ru.tinkoff.kora.database.symbol.processor.vertx.TestEntityVertxRowMapperNonFinal
import ru.tinkoff.kora.database.symbol.processor.vertx.TestEntityVertxRowMapperNonNull
import ru.tinkoff.kora.database.vertx.VertxRepository

@Repository
interface AllowedResultsRepository : VertxRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    fun returnVoid()

    @Query("SELECT test")
    fun returnPrimitive(): Int

    @Query("SELECT test")
    fun returnObject(): Int?

    @Query("SELECT test")
    fun returnNullableObject(): Int?

    @Query("SELECT test")
    @Mapping(TestEntityVertxRowMapper::class)
    fun returnObjectWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityVertxRowMapperNonFinal::class)
    fun returnObjectWithRowMapperNonFinal(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityVertxRowMapper::class)
    fun returnOptionalWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityVertxRowMapperNonNull::class)
    fun returnListWithRowMapper(): List<TestEntity>
}
