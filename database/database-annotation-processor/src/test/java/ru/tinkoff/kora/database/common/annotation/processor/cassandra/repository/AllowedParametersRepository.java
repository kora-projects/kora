package ru.tinkoff.kora.database.common.annotation.processor.cassandra.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.database.cassandra.CassandraRepository;
import ru.tinkoff.kora.database.common.annotation.Batch;
import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.common.annotation.processor.cassandra.CassandraEntity;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AllowedParametersRepository extends CassandraRepository {
    @Query("INSERT INTO test(test) VALUES ('test')")
    void connectionParameter(CqlSession connection);

    @Query("INSERT INTO test(value1, value2) VALUES (:value1, :value2)")
    void nativeParameter(String value1, int value2);

    @Query("INSERT INTO test(value1, value2) VALUES (:entity.field1, :entity.field2, :entity.field3, :entity.unknownTypeField, :entity.mappedField1, :entity.mappedField2)")
    void dtoJavaBeanParameter(TestEntityJavaBean entity);

    @Query("INSERT INTO test(value1, value2) VALUES (:entity.field1, :entity.field2, :entity.field3, :entity.unknownTypeField, :entity.mappedField1, :entity.mappedField2)")
    Mono<Void> dtoJavaBeanParameterMono(TestEntityJavaBean entity);

    @Query("INSERT INTO test(value1, value2) VALUES (:entity.field1, :entity.field2, :entity.field3, :entity.unknownTypeField, :entity.mappedField1, :entity.mappedField2)")
    Flux<Void> dtoJavaBeanParameterFlux(TestEntityJavaBean entity);

    @Query("INSERT INTO test(value1, value2) VALUES (:entity.field1, :entity.field2, :entity.field3, :entity.unknownTypeField, :entity.mappedField1, :entity.mappedField2)")
    void dtoRecordParameterMapping(TestEntityRecord entity);

    @Query("INSERT INTO test(value1, value2) VALUES (:value1, :value2)")
    void nativeParameterBatch(@Batch List<String> value1, int value2);

    @Query("INSERT INTO test(value1, value2) VALUES (:entity.field1, :entity.field2, :entity.field3, :entity.unknownTypeField, :entity.mappedField1, :entity.mappedField2)")
    void dtoBatch(@Batch List<TestEntityJavaBean> entity);

    @Query("""
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
        """)
    void allNativeParameters(
        boolean booleanPrimitive,
        @Nullable Boolean booleanBoxed,
        int integerPrimitive,
        @Nullable Integer integerBoxed,
        long longPrimitive,
        @Nullable Long longBoxed,
        double doublePrimitive,
        @Nullable Double doubleBoxed,
        String string,
        BigDecimal bigDecimal,
        LocalDateTime localDateTime,
        LocalDate localDate
    );

    @Query("""
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
        """)
    void allNativeParametersDto(CassandraEntity.AllNativeTypesEntity entity);


    @Query("INSERT INTO test(value1, value2) VALUES (:value, :valueTest)")
    void parametersWithSimilarNames(String value, int valueTest);

}
