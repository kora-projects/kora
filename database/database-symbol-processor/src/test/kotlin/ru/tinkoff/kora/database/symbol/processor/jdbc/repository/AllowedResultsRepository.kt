package ru.tinkoff.kora.database.symbol.processor.jdbc.repository

import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.jdbc.JdbcRepository
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.symbol.processor.jdbc.TestEntityJdbcRowMapper
import ru.tinkoff.kora.database.symbol.processor.jdbc.TestEntityJdbcRowMapperNonFinal

@Repository
interface AllowedResultsRepository : JdbcRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    fun returnVoid()

    @Query("SELECT test")
    fun returnPrimitive(): Int

    @Query("SELECT test")
    fun returnObject(): Int?

    @Query("SELECT test")
    fun returnNullableObject(): Int?

    @Query("SELECT test")
    @Mapping(TestEntityJdbcRowMapper::class)
    fun returnObjectWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityJdbcRowMapperNonFinal::class)
    fun returnObjectWithRowMapperNonFinal(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityJdbcRowMapper::class)
    fun returnOptionalWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityJdbcRowMapper::class)
    fun returnListWithRowMapper(): List<TestEntity>
}
