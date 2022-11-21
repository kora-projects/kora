package ru.tinkoff.kora.database.symbol.processor.r2dbc.repository

import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.r2dbc.R2dbcRepository
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.symbol.processor.r2dbc.TestEntityR2dbcRowMapper
import ru.tinkoff.kora.database.symbol.processor.r2dbc.TestEntityR2dbcRowMapperNonFinal

@Repository
interface AllowedResultsRepository : R2dbcRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    fun returnVoid()

    @Query("SELECT test")
    fun returnPrimitive(): Int

    @Query("SELECT test")
    fun returnObject(): Int?

    @Query("SELECT test")
    fun returnNullableObject(): Int?

    @Query("SELECT test")
    @Mapping(TestEntityR2dbcRowMapper::class)
    fun returnObjectWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityR2dbcRowMapperNonFinal::class)
    fun returnObjectWithRowMapperNonFinal(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityR2dbcRowMapper::class)
    fun returnOptionalWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityR2dbcRowMapper::class)
    fun returnListWithRowMapper(): List<TestEntity>
}
