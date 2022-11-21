package ru.tinkoff.kora.database.symbol.processor.jdbc.repository

import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.jdbc.JdbcRepository
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.symbol.processor.jdbc.TestEntityJdbcRowMapper
import ru.tinkoff.kora.database.symbol.processor.jdbc.TestEntityJdbcRowMapperNonFinal

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
