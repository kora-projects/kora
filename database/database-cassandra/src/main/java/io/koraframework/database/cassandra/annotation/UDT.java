package io.koraframework.database.cassandra.annotation;

import io.koraframework.database.cassandra.mapper.parameter.CassandraParameterColumnMapper;
import io.koraframework.database.cassandra.mapper.result.CassandraRowColumnMapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Обозначает пользовательский
 * <a href="https://docs.datastax.com/en/cql-oss/3.3/cql/cql_using/useCreateUDT.html">UDT тип</a>
 * Cassandra, для которого будут сгенерированы конвертеры чтения и записи.
 * <hr>
 * <b>English</b>: Marks a Cassandra
 * <a href="https://docs.datastax.com/en/cql-oss/3.3/cql/cql_using/useCreateUDT.html">UDT type</a>
 * that should have read and write converters generated for it.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @UDT
 * public record Address(String city, String street) {}
 *
 * @EntityCassandra
 * public record User(String id, Address address) {}
 * }
 * </pre>
 *
 * @see CassandraParameterColumnMapper
 * @see CassandraRowColumnMapper
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UDT {
}
