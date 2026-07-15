package io.koraframework.database.jdbc;

import io.koraframework.database.jdbc.mapper.parameter.CollectionParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.CollectionFromSqlArrayResultColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.mapper.result.ListCollectionFactory;
import io.koraframework.database.jdbc.mapper.result.SetCollectionFactory;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface SqlArrayJdbcColumnMappersModule {

    default <T> JdbcParameterColumnMapper<Collection<T>> collectionJdbcParameterColumnMapper(ArrayColumnData<T> arrayColumnData) {
        return new CollectionParameterColumnMapper<>(arrayColumnData);
    }

    default <T> JdbcParameterColumnMapper<List<T>> listJdbcParameterColumnMapper(ArrayColumnData<T> arrayColumnData) {
        return new CollectionParameterColumnMapper<>(arrayColumnData);
    }

    default <T> JdbcParameterColumnMapper<Set<T>> setJdbcParameterColumnMapper(ArrayColumnData<T> arrayColumnData) {
        return new CollectionParameterColumnMapper<>(arrayColumnData);
    }

    default <T> JdbcResultColumnMapper<List<T>> listJdbcResultColumnMapper(ArrayColumnData<T> arrayColumnData) {
        return new CollectionFromSqlArrayResultColumnMapper<>(arrayColumnData, new ListCollectionFactory<>());
    }

    default <T> JdbcResultColumnMapper<Set<T>> setJdbcResultColumnMapper(ArrayColumnData<T> arrayColumnData) {
        return new CollectionFromSqlArrayResultColumnMapper<>(arrayColumnData, new SetCollectionFactory<>());
    }
}
