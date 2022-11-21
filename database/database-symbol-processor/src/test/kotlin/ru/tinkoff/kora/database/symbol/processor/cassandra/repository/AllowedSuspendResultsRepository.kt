package ru.tinkoff.kora.database.symbol.processor.cassandra.repository

import kotlinx.coroutines.flow.Flow
import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.database.cassandra.CassandraRepository
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.symbol.processor.cassandra.TestEntityCassandraRowMapper
import ru.tinkoff.kora.database.symbol.processor.cassandra.TestEntityCassandraRowMapperNonFinal
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity

@Repository
interface AllowedSuspendResultsRepository : CassandraRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    suspend fun returnVoid()

    @Query("SELECT test")
    suspend fun returnPrimitive(): Int

    @Query("SELECT test")
    suspend fun returnObject(): Int?

    @Query("SELECT test")
    suspend fun returnNullableObject(): Int?

    @Query("SELECT test")
    @Mapping(TestEntityCassandraRowMapper::class)
    suspend fun returnObjectWithRowMapper(): TestEntity

    @Query("SELECT test")
    @Mapping(TestEntityCassandraRowMapperNonFinal::class)
    suspend fun returnObjectWithRowMapperNonFinal(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityCassandraRowMapper::class)
    suspend fun returnOptionalWithRowMapper(): TestEntity?

    @Query("SELECT test")
    @Mapping(TestEntityCassandraRowMapper::class)
    suspend fun returnListWithRowMapper(): List<TestEntity>

    @Query("SELECT test")
    fun returnObjectFlow(): Flow<Int>

    @Query("SELECT test")
    @Mapping(TestEntityCassandraRowMapper::class)
    fun returnObjectFlowWithRowMapper(): Flow<TestEntity>

    @Query("SELECT test")
    @Mapping(TestEntityCassandraRowMapperNonFinal::class)
    fun returnObjectFlowWithRowMapperNonFinal(): Flow<TestEntity>


}
