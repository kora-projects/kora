package ru.tinkoff.kora.database.symbol.processor.jdbc.repository

import ru.tinkoff.kora.database.common.annotation.Batch
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.jdbc.JdbcRepository
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.symbol.processor.jdbc.AllNativeTypesEntity
import java.math.BigDecimal
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime


@Repository
interface AllowedParametersRepository : JdbcRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    fun connectionParameter(connection: Connection)

    @Query("INSERT INTO test(value1, value2) VALUES (:value1, :value2)")
    fun nativeParameter(value1: String?, value2: Int)

    @Query("INSERT INTO test(value1, value2) VALUES (:entity.field1, :entity.field2, :entity.field3, :entity.unknownTypeField, :entity.mappedField1, :entity.mappedField2)")
    fun dtoJavaBeanParameter(entity: TestEntity)

    @Query("INSERT INTO test(value1, value2) VALUES (:unknownField)")
    fun unknownTypeFieldParameter(unknownField: TestEntity.UnknownField?)

    @Query("INSERT INTO test(value1, value2) VALUES (:entity.field1, :entity.field2, :entity.field3, :entity.unknownTypeField, :entity.mappedField1, :entity.mappedField2)")
    suspend fun dtoJavaBeanParameterMono(entity: TestEntity?)

    @Query("INSERT INTO test(value1, value2) VALUES (:entity.field1, :entity.field2, :entity.field3, :entity.unknownTypeField, :entity.mappedField1, :entity.mappedField2)")
    fun dtoRecordParameterMapping(entity: TestEntity?)

    @Query("INSERT INTO test(value1, value2) VALUES (:value1, :value2)")
    fun nativeParameterBatch(@Batch value1: List<String?>, value2: Int)

    @Query("INSERT INTO test(value1, value2) VALUES (:entity.field1, :entity.field2, :entity.field3, :entity.unknownTypeField, :entity.mappedField1, :entity.mappedField2)")
    fun dtoBatch(@Batch entity: List<TestEntity?>)

    @Query(
        """
        INSERT INTO test(...) VALUES (
          :booleanPrimitive,
          :booleanBoxed,
          :integerPrimitive,
          :integerBoxed,
          :longPrimitive,
          :longBoxed,
          :doublePrimitive,
          :doubleBoxed,
          :string,
          :bigDecimal,
          :localDateTime,
          :localDate
         )        
    """
    )
    fun allNativeParameters(
        booleanPrimitive: Boolean,
        booleanBoxed: Boolean?,
        integerPrimitive: Int,
        integerBoxed: Int?,
        longPrimitive: Long,
        longBoxed: Long?,
        doublePrimitive: Double,
        doubleBoxed: Double?,
        string: String?,
        bigDecimal: BigDecimal?,
        localDateTime: LocalDateTime?,
        localDate: LocalDate?
    )

    @Query(
        """
        INSERT INTO test(...) VALUES (
          :entity.booleanPrimitive,
          :entity.booleanBoxed,
          :entity.integerPrimitive,
          :entity.integerBoxed,
          :entity.longPrimitive,
          :entity.longBoxed,
          :entity.doublePrimitive,
          :entity.doubleBoxed,
          :entity.string,
          :entity.bigDecimal,
          :entity.byteArray,
          :entity.localDateTime,
          :entity.localDate
        )
    """
    )
    fun allNativeParametersDto(entity: AllNativeTypesEntity?)

    @Query("INSERT INTO test(value1, value2) VALUES (:value, :valueTest)")
    fun parametersWithSimilarNames(value: String?, valueTest: Int)
}
