package io.koraframework.database.jdbc;

import io.koraframework.database.jdbc.exception.UncheckedSqlException;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import org.jspecify.annotations.Nullable;

import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Immutable JDBC query descriptor that can prepare a {@link PreparedStatement} and bind all parameters.
 *
 * @see JdbcExecutor#query(JdbcQuery, JdbcExecutor.SqlFunction)
 */
public interface JdbcQuery {

    static NamedQueryBuilder named() {
        return JdbcQueryImpl.named();
    }

    static TemplateBuilder template() {
        return JdbcQueryImpl.template();
    }

    static JdbcQuery template(String sql, @Nullable Object... args) {
        return template()
            .sql(sql)
            .bindAll(args)
            .build();
    }

    String sourceSql();

    String sql();

    JdbcQueryOptions options();

    List<Object> parameterValues();

    List<Parameter> parameters();

    PreparedStatement prepare(Connection connection) throws UncheckedSqlException;

    record Parameter(String name, @Nullable Object value, JdbcParameterBinder binder) {}

    @FunctionalInterface
    interface JdbcParameterBinder {
        void set(PreparedStatement statement, int index) throws SQLException;
    }

    interface NamedQueryBuilder {

        NamedQueryBuilder sql(String sql);

        default NamedQueryBuilder sql(String sql, Object... args) {
            return this.sql(sql.formatted(args));
        }

        default NamedQueryBuilder sqlIf(String sql, boolean condition) {
            return condition
                ? this.sql(sql)
                : this;
        }

        default NamedQueryBuilder sqlIf(String sql, boolean condition, Object... args) {
            return condition
                ? this.sql(sql, args)
                : this;
        }

        default NamedQueryBuilder opts(Consumer<OptsBuilder> options) {
            var builder = OptsBuilder.builder();
            options.accept(builder);
            return this.opts(builder.build());
        }

        NamedQueryBuilder opts(JdbcQueryOptions options);

        NamedQueryBuilder bind(String name, @Nullable Object value);

        NamedQueryBuilder bind(String name, @Nullable Object value, int sqlType);

        default NamedQueryBuilder bind(String name, @Nullable Object value, JDBCType sqlType) {
            return bind(name, value, sqlType.getVendorTypeNumber());
        }

        NamedQueryBuilder bind(String name, @Nullable Object value, SQLType sqlType);

        <T> NamedQueryBuilder bind(String name, @Nullable T value, JdbcParameterColumnMapper<T> mapper);

        NamedQueryBuilder bind(String name, JdbcParameterBinder parameter);

        default NamedQueryBuilder bindAll(Map<String, ?> values) {
            for (var entry : values.entrySet()) {
                this.bind(entry.getKey(), entry.getValue());
            }
            return this;
        }

        default NamedQueryBuilder bindIf(String name, @Nullable Object value, boolean condition) {
            return condition
                ? this.bind(name, value)
                : this;
        }

        default NamedQueryBuilder bindIf(String name, @Nullable Object value, int sqlType, boolean condition) {
            return condition
                ? this.bind(name, value, sqlType)
                : this;
        }

        default NamedQueryBuilder bindIf(String name, @Nullable Object value, JDBCType sqlType, boolean condition) {
            return condition
                ? this.bind(name, value, sqlType)
                : this;
        }

        default NamedQueryBuilder bindIf(String name, @Nullable Object value, SQLType sqlType, boolean condition) {
            return condition
                ? this.bind(name, value, sqlType)
                : this;
        }

        default <T> NamedQueryBuilder bindIf(String name, @Nullable T value, JdbcParameterColumnMapper<T> mapper, boolean condition) {
            return condition
                ? this.bind(name, value, mapper)
                : this;
        }

        default NamedQueryBuilder bindIf(String name, JdbcParameterBinder parameter, boolean condition) {
            return condition
                ? this.bind(name, parameter)
                : this;
        }

        JdbcQuery build();

        NamedBatchBuilder batch();
    }

    interface TemplateBuilder {

        TemplateBuilder sql(String sql);

        default TemplateBuilder sql(String sql, Object... args) {
            return this.sql(sql.formatted(args));
        }

        default TemplateBuilder sqlIf(String sql, boolean condition) {
            return condition
                ? this.sql(sql)
                : this;
        }

        default TemplateBuilder sqlIf(String sql, boolean condition, Object... args) {
            return condition
                ? this.sql(sql, args)
                : this;
        }

        default TemplateBuilder opts(Consumer<OptsBuilder> options) {
            var builder = OptsBuilder.builder();
            options.accept(builder);
            return this.opts(builder.build());
        }

        TemplateBuilder opts(JdbcQueryOptions options);

        TemplateBuilder bind(@Nullable Object value);

        TemplateBuilder bind(@Nullable Object value, int sqlType);

        default TemplateBuilder bind(@Nullable Object value, JDBCType sqlType) {
            return this.bind(value, sqlType.getVendorTypeNumber());
        }

        TemplateBuilder bind(@Nullable Object value, SQLType sqlType);

        <T> TemplateBuilder bind(@Nullable T value, JdbcParameterColumnMapper<T> mapper);

        default TemplateBuilder bindAll(@Nullable Object... values) {
            for (var value : values) {
                this.bind(value);
            }
            return this;
        }

        JdbcQuery build();

        TemplateBatchBuilder batch();
    }

    interface JdbcQueryBatch {

        String sourceSql();

        String sql();

        JdbcQueryOptions options();

        int size();

        List<Parameter> parameters(int index);

        PreparedStatement prepare(Connection connection) throws UncheckedSqlException;
    }

    interface OptsBuilder {

        static OptsBuilder builder() {
            return JdbcQueryImpl.opts();
        }

        OptsBuilder fetchSize(int fetchSize);

        OptsBuilder maxRows(int maxRows);

        OptsBuilder queryTimeoutSeconds(int seconds);

        default OptsBuilder resultSetType(JdbcQueryOptions.ResultSetType type) {
            return this.resultSetType(type.value());
        }

        OptsBuilder resultSetType(int type);

        default OptsBuilder resultSetConcurrency(JdbcQueryOptions.ResultSetConcurrency concurrency) {
            return this.resultSetConcurrency(concurrency.value());
        }

        OptsBuilder resultSetConcurrency(int concurrency);

        default OptsBuilder resultSetHoldability(JdbcQueryOptions.ResultSetHoldability holdability) {
            return this.resultSetHoldability(holdability.value());
        }

        OptsBuilder resultSetHoldability(int holdability);

        default OptsBuilder generatedKeys(JdbcQueryOptions.GeneratedKeys generatedKeys) {
            return this.generatedKeys(generatedKeys.value());
        }

        OptsBuilder generatedKeys(int generatedKeys);

        default OptsBuilder returnGeneratedKeys() {
            return this.generatedKeys(JdbcQueryOptions.GeneratedKeys.RETURN_GENERATED_KEYS);
        }

        OptsBuilder returnGeneratedKeys(String... columns);

        JdbcQueryOptions build();
    }

    interface NamedBatchBuilder {

        NamedBatchBuilder bind(Map<String, ?> values);

        NamedBatchBuilder bind(Consumer<NamedRowBinder> binder);

        default <T> NamedBatchBuilder bindAll(Iterable<T> values, BiConsumer<NamedRowBinder, T> binder) {
            for (var value : values) {
                this.bind(row -> binder.accept(row, value));
            }
            return this;
        }

        <T> NamedBatchBuilder bindAllSql(Iterable<T> values, JdbcNamedBatchBinder<T> binder);

        JdbcQueryBatch build();
    }

    interface TemplateBatchBuilder {

        TemplateBatchBuilder bind(@Nullable Object... values);

        TemplateBatchBuilder bind(Consumer<TemplateRowBinder> binder);

        default <T> TemplateBatchBuilder bindAll(Iterable<T> values, BiConsumer<TemplateRowBinder, T> binder) {
            for (var value : values) {
                this.bind(row -> binder.accept(row, value));
            }
            return this;
        }

        <T> TemplateBatchBuilder bindAllSql(Iterable<T> values, JdbcTemplateBatchBinder<T> binder);

        JdbcQueryBatch build();
    }

    interface NamedRowBinder {

        NamedRowBinder bind(String name, @Nullable Object value);

        NamedRowBinder bind(String name, @Nullable Object value, int sqlType);

        default NamedRowBinder bind(String name, @Nullable Object value, JDBCType sqlType) {
            return this.bind(name, value, sqlType.getVendorTypeNumber());
        }

        NamedRowBinder bind(String name, @Nullable Object value, SQLType sqlType);

        <T> NamedRowBinder bind(String name, @Nullable T value, JdbcParameterColumnMapper<T> mapper);

        default NamedRowBinder bindAll(Map<String, ?> values) {
            for (var entry : values.entrySet()) {
                this.bind(entry.getKey(), entry.getValue());
            }
            return this;
        }
    }

    interface TemplateRowBinder {

        TemplateRowBinder bind(@Nullable Object value);

        TemplateRowBinder bind(@Nullable Object value, int sqlType);

        default TemplateRowBinder bind(@Nullable Object value, JDBCType sqlType) {
            return this.bind(value, sqlType.getVendorTypeNumber());
        }

        TemplateRowBinder bind(@Nullable Object value, SQLType sqlType);

        <T> TemplateRowBinder bind(@Nullable T value, JdbcParameterColumnMapper<T> mapper);

        default TemplateRowBinder bindAll(@Nullable Object... values) {
            for (var value : values) {
                this.bind(value);
            }
            return this;
        }
    }

    @FunctionalInterface
    interface JdbcNamedBatchBinder<T> {
        void bind(NamedRowBinder binder, T value) throws SQLException;
    }

    @FunctionalInterface
    interface JdbcTemplateBatchBinder<T> {
        void bind(TemplateRowBinder binder, T value) throws SQLException;
    }
}
