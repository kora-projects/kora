package ru.tinkoff.kora.database.symbol.processor.cassandra.repository

import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.database.cassandra.CassandraRepository
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.symbol.processor.cassandra.TestEntityCassandraRowMapper
import ru.tinkoff.kora.database.symbol.processor.cassandra.TestEntityCassandraRowMapperNonFinal
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity

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
