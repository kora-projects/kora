package io.koraframework.database.jdbc;

import io.koraframework.database.jdbc.exception.UncheckedSqlException;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import org.jspecify.annotations.Nullable;

import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Immutable JDBC query descriptor that can create a {@link PreparedStatement} and bind all parameters.
 * <p>
 * A query stores original SQL, executable JDBC SQL, statement options, and binders in the order
 * required by JDBC.
 *
 * @see JdbcExecutor#query(JdbcQuery, JdbcExecutor.SqlFunction)
 */
public interface JdbcQuery {

    /**
     * Creates a builder for SQL with named parameters.
     * <p>
     * Use named queries when SQL is easier to read with {@code :name} placeholders. Parameters are
     * bound separately by name, then converted to JDBC {@code ?} placeholders when {@link NamedQueryBuilder#build()}
     * is called. {@link NamedQueryBuilder#bindIn(String, Iterable)} expands values for
     * {@code IN (:ids)} clauses.
     * <pre>{@code
     * var query = JdbcQuery.named()
     *     .sql("SELECT id, name FROM %s WHERE status = :status".formatted(tableName))
     *     .bind("status", status)
     *     .sqlIf(" AND id IN (:ids)", !ids.isEmpty())
     *     .bindInIf("ids", ids, !ids.isEmpty())
     *     .build();
     *
     * var users = jdbc.queryList(query, row -> new User(row.getLong("id"), row.getString("name")));
     * }</pre>
     * SQL identifiers or fragments should be formatted before they are passed to the builder.
     * User values should be passed through {@code bind(...)}.
     *
     * @return named query builder
     */
    static NamedQueryBuilder named() {
        return JdbcQueryImpl.named();
    }

    /**
     * Creates a builder for SQL with positional JDBC placeholders.
     * <p>
     * Use template queries when SQL already has JDBC {@code ?} placeholders and parameters are bound
     * by position.
     * <pre>{@code
     * var query = JdbcQuery.template()
     *     .sql("SELECT id, name FROM %s WHERE 1 = 1".formatted(tableName))
     *     .sqlIf(" AND status = ?", status != null, status)
     *     .sqlIf(" AND created_at >= ?", createdAfter != null, createdAfter)
     *     .sql(" ORDER BY id")
     *     .build();
     *
     * var users = jdbc.queryList(query, row -> new User(row.getLong("id"), row.getString("name")));
     * }</pre>
     * SQL identifiers or fragments should be formatted before they are passed to the builder.
     * User values should be passed through {@code bind(...)}.
     *
     * @return template query builder
     */
    static TemplateBuilder template() {
        return JdbcQueryImpl.template();
    }

    /**
     * Creates a positional query in one call.
     * <p>
     * This is a compact form of {@link #template()} for simple SQL with JDBC {@code ?} placeholders.
     * Arguments are bound in placeholder order.
     * <pre>{@code
     * var query = JdbcQuery.template(
     *     "SELECT id, name FROM users WHERE status = ? AND created_at > ?",
     *     status,
     *     createdAfter
     * );
     *
     * var users = jdbc.queryList(query, row -> new User(row.getLong("id"), row.getString("name")));
     * }</pre>
     *
     * @param sql  SQL with JDBC {@code ?} placeholders
     * @param args parameter values in placeholder order
     * @return built query
     */
    static JdbcQuery template(String sql, @Nullable Object... args) {
        return template()
            .sql(sql)
            .bindAll(args)
            .build();
    }

    /**
     * @return original SQL before named parameters are converted
     */
    String sourceSql();

    /**
     * @return executable JDBC SQL with {@code ?} placeholders
     */
    String sql();

    JdbcQueryOptions options();

    /**
     * @return full parameter descriptors in JDBC binding order
     */
    List<Parameter> parameters();

    PreparedStatement prepare(Connection connection) throws UncheckedSqlException;

    /**
     * Builder for SQL with named parameters.
     * <p>
     * SQL fragments and parameter values are added separately: {@link #sql(String)} changes SQL,
     * {@link #bind(String, Object)} supplies values. All named parameters used in SQL must be bound,
     * and all bound parameters must be used in SQL.
     * <pre>{@code
     * var query = JdbcQuery.named()
     *     .sql("SELECT id, name FROM users WHERE 1 = 1")
     *     .sqlIf(" AND status = :status", status != null)
     *     .bindIf("status", status, status != null)
     *     .sql(" ORDER BY id")
     *     .build();
     * }</pre>
     */
    interface NamedQueryBuilder {

        NamedQueryBuilder sql(String sql);

        default NamedQueryBuilder sqlIf(String sql, boolean condition) {
            return condition
                ? this.sql(sql)
                : this;
        }

        default NamedQueryBuilder opts(Consumer<OptsBuilder> options) {
            var builder = OptsBuilder.builder();
            options.accept(builder);
            return this.opts(builder.build());
        }

        NamedQueryBuilder opts(JdbcQueryOptions options);

        /**
         * Binds a named parameter value.
         * <pre>{@code
         * JdbcQuery.named()
         *     .sql("SELECT * FROM users WHERE id = :id")
         *     .bind("id", id);
         * }</pre>
         *
         * @return this builder
         */
        NamedQueryBuilder bind(String name, @Nullable Object value);

        NamedQueryBuilder bind(String name, @Nullable Object value, int sqlType);

        default NamedQueryBuilder bind(String name, @Nullable Object value, JDBCType sqlType) {
            return bind(name, value, sqlType.getVendorTypeNumber());
        }

        NamedQueryBuilder bind(String name, @Nullable Object value, SQLType sqlType);

        <T> NamedQueryBuilder bind(String name, @Nullable T value, JdbcParameterColumnMapper<T> mapper);

        NamedQueryBuilder bind(String name, JdbcParameterBinder parameter);

        /**
         * Binds and expands a named parameter value for an {@code IN (:name)} clause.
         * <p>
         * Unlike {@link #bind(String, Object)}, this method intentionally creates one JDBC
         * placeholder per iterable item. Use regular {@code bind(...)} for scalar values,
         * collections stored as one database value, arrays, or {@code byte[]}.
         * <pre>{@code
         * JdbcQuery.named()
         *     .sql("SELECT * FROM users WHERE id IN (:ids)")
         *     .bindIn("ids", ids);
         * }</pre>
         *
         * @return this builder
         */
        NamedQueryBuilder bindIn(String name, Iterable<?> values);

        NamedQueryBuilder bindIn(String name, Iterable<?> values, int sqlType);

        default NamedQueryBuilder bindIn(String name, Iterable<?> values, JDBCType sqlType) {
            return bindIn(name, values, sqlType.getVendorTypeNumber());
        }

        NamedQueryBuilder bindIn(String name, Iterable<?> values, SQLType sqlType);

        <T> NamedQueryBuilder bindIn(String name, Iterable<T> values, JdbcParameterColumnMapper<T> mapper);

        /**
         * Binds all map entries as named parameters.
         * <pre>{@code
         * var values = Map.of("id", id, "status", status);
         *
         * JdbcQuery.named()
         *     .sql("SELECT * FROM users WHERE id = :id AND status = :status")
         *     .bindAll(values);
         * }</pre>
         *
         * @return this builder
         */
        default NamedQueryBuilder bindAll(Map<String, ?> values) {
            for (var entry : values.entrySet()) {
                this.bind(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * Binds a named parameter only when condition is true.
         * <pre>{@code
         * JdbcQuery.named()
         *     .sql("SELECT * FROM users WHERE 1 = 1")
         *     .sqlIf(" AND status = :status", status != null)
         *     .bindIf("status", status, status != null);
         * }</pre>
         *
         * @return this builder
         */
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

        default NamedQueryBuilder bindInIf(String name, Iterable<?> values, boolean condition) {
            return condition
                ? this.bindIn(name, values)
                : this;
        }

        default NamedQueryBuilder bindInIf(String name, Iterable<?> values, int sqlType, boolean condition) {
            return condition
                ? this.bindIn(name, values, sqlType)
                : this;
        }

        default NamedQueryBuilder bindInIf(String name, Iterable<?> values, JDBCType sqlType, boolean condition) {
            return condition
                ? this.bindIn(name, values, sqlType)
                : this;
        }

        default NamedQueryBuilder bindInIf(String name, Iterable<?> values, SQLType sqlType, boolean condition) {
            return condition
                ? this.bindIn(name, values, sqlType)
                : this;
        }

        default <T> NamedQueryBuilder bindInIf(String name, Iterable<T> values, JdbcParameterColumnMapper<T> mapper, boolean condition) {
            return condition
                ? this.bindIn(name, values, mapper)
                : this;
        }

        JdbcQuery build();

        /**
         * Starts batch builder for this SQL.
         * <p>
         * Use it when the same named SQL should be executed many times with different parameter
         * values.
         * <pre>{@code
         * var batch = JdbcQuery.named()
         *     .sql("INSERT INTO users(name, status) VALUES (:name, :status)")
         *     .batch()
         *     .bindAll(users, (row, user) -> row
         *         .bind("name", user.name())
         *         .bind("status", user.status()))
         *     .build();
         *
         * var count = jdbc.executeUpdateBatch(batch);
         * }</pre>
         *
         * @return named batch builder
         */
        NamedBatchBuilder batch();
    }

    /**
     * Builder for SQL with positional JDBC {@code ?} placeholders.
     * <pre>{@code
     * var query = JdbcQuery.template()
     *     .sql("SELECT id, name FROM users WHERE 1 = 1")
     *     .sqlIf(" AND status = ?", status != null, status)
     *     .sql(" ORDER BY id")
     *     .build();
     * }</pre>
     */
    interface TemplateBuilder {

        TemplateBuilder sql(String sql);

        default TemplateBuilder sqlIf(String sql, boolean condition) {
            return condition
                ? this.sql(sql)
                : this;
        }

        /**
         * Appends SQL and binds positional values only when condition is true.
         * <pre>{@code
         * JdbcQuery.template()
         *     .sql("SELECT * FROM users WHERE 1 = 1")
         *     .sqlIf(" AND status = ?", status != null, status)
         *     .sqlIf(" AND created_at >= ?", createdAfter != null, createdAfter);
         * }</pre>
         *
         * @return this builder
         */
        default TemplateBuilder sqlIf(String sql, boolean condition, @Nullable Object... args) {
            if (condition) {
                this.sql(sql);
                this.bindAll(args);
            }
            return this;
        }

        default TemplateBuilder opts(Consumer<OptsBuilder> options) {
            var builder = OptsBuilder.builder();
            options.accept(builder);
            return this.opts(builder.build());
        }

        /**
         * @return this builder after setting statement options
         */
        TemplateBuilder opts(JdbcQueryOptions options);

        /**
         * Binds next positional parameter value.
         * <pre>{@code
         * JdbcQuery.template()
         *     .sql("SELECT * FROM users WHERE id = ?")
         *     .bind(id);
         * }</pre>
         *
         * @return this builder
         */
        TemplateBuilder bind(@Nullable Object value);

        TemplateBuilder bind(@Nullable Object value, int sqlType);

        default TemplateBuilder bind(@Nullable Object value, JDBCType sqlType) {
            return this.bind(value, sqlType.getVendorTypeNumber());
        }

        TemplateBuilder bind(@Nullable Object value, SQLType sqlType);

        <T> TemplateBuilder bind(@Nullable T value, JdbcParameterColumnMapper<T> mapper);

        /**
         * Binds values in placeholder order.
         * <pre>{@code
         * JdbcQuery.template()
         *     .sql("SELECT * FROM users WHERE status = ? AND created_at >= ?")
         *     .bindAll(status, createdAfter);
         * }</pre>
         *
         * @return this builder
         */
        default TemplateBuilder bindAll(@Nullable Object... values) {
            for (var value : values) {
                this.bind(value);
            }
            return this;
        }

        JdbcQuery build();

        /**
         * Starts batch builder for this SQL.
         * <p>
         * Use it when the same positional SQL should be executed many times with different parameter
         * values.
         * <pre>{@code
         * var batch = JdbcQuery.template()
         *     .sql("INSERT INTO users(name, status) VALUES (?, ?)")
         *     .batch()
         *     .bindAll(users, (row, user) -> row
         *         .bind(user.name())
         *         .bind(user.status()))
         *     .build();
         *
         * var count = jdbc.executeUpdateBatch(batch);
         * }</pre>
         *
         * @return template batch builder
         */
        TemplateBatchBuilder batch();
    }

    /**
     * Immutable batch query descriptor.
     */
    interface JdbcQueryBatch {

        /**
         * @return original batch SQL
         */
        String sourceSql();

        /**
         * @return executable JDBC SQL
         */
        String sql();

        /**
         * @return statement options used when preparing this batch
         */
        JdbcQueryOptions options();

        /**
         * @return number of rows in this batch
         */
        int size();

        /**
         * @param index row index
         * @return Returns parameter descriptors for batch row by index.
         */
        List<Parameter> parameters(int index);

        PreparedStatement prepare(Connection connection) throws UncheckedSqlException;
    }

    interface OptsBuilder {

        static OptsBuilder builder() {
            return JdbcQueryImpl.opts();
        }

        /**
         * @return this builder after setting JDBC fetch size
         * @see Statement#setFetchSize(int)
         */
        OptsBuilder fetchSize(int fetchSize);

        /**
         * @return this builder after setting maximum rows for the statement
         * @see Statement#setMaxRows(int)
         */
        OptsBuilder maxRows(int maxRows);

        /**
         * @return this builder after setting query timeout in seconds
         * @see Statement#setQueryTimeout(int)
         */
        OptsBuilder queryTimeoutSeconds(int seconds);

        default OptsBuilder queryTimeout(Duration duration) {
            return queryTimeoutSeconds(Math.min(1, duration.toSecondsPart()));
        }

        /**
         * @return this builder after setting result set type
         * @see Connection#prepareStatement(String, int, int)
         */
        default OptsBuilder resultSetType(JdbcQueryOptions.ResultSetType type) {
            return this.resultSetType(type.value());
        }

        /**
         * @return this builder after setting result set type
         * @see Connection#prepareStatement(String, int, int)
         */
        OptsBuilder resultSetType(int type);

        /**
         * @return this builder after setting result set concurrency
         * @see Connection#prepareStatement(String, int, int)
         */
        default OptsBuilder resultSetConcurrency(JdbcQueryOptions.ResultSetConcurrency concurrency) {
            return this.resultSetConcurrency(concurrency.value());
        }

        /**
         * @return this builder after setting result set concurrency
         * @see Connection#prepareStatement(String, int, int)
         */
        OptsBuilder resultSetConcurrency(int concurrency);

        /**
         * @return this builder after setting result set holdability
         * @see Connection#prepareStatement(String, int, int, int)
         */
        default OptsBuilder resultSetHoldability(JdbcQueryOptions.ResultSetHoldability holdability) {
            return this.resultSetHoldability(holdability.value());
        }

        /**
         * @return this builder after setting result set holdability
         * @see Connection#prepareStatement(String, int, int, int)
         */
        OptsBuilder resultSetHoldability(int holdability);

        /**
         * @return this builder after setting generated keys mode
         * @see Connection#prepareStatement(String, int)
         * @see Statement#RETURN_GENERATED_KEYS
         * @see Statement#NO_GENERATED_KEYS
         */
        default OptsBuilder generatedKeys(JdbcQueryOptions.GeneratedKeys generatedKeys) {
            return this.generatedKeys(generatedKeys.value());
        }

        /**
         * @return this builder after setting generated keys mode
         * @see Connection#prepareStatement(String, int)
         * @see Statement#RETURN_GENERATED_KEYS
         * @see Statement#NO_GENERATED_KEYS
         */
        OptsBuilder generatedKeys(int generatedKeys);

        /**
         * @return this builder after requesting generated keys
         * @see Connection#prepareStatement(String, int)
         * @see Statement#RETURN_GENERATED_KEYS
         */
        default OptsBuilder returnGeneratedKeys() {
            return this.generatedKeys(JdbcQueryOptions.GeneratedKeys.RETURN_GENERATED_KEYS);
        }

        /**
         * @return this builder after requesting generated keys by column names
         * @see Connection#prepareStatement(String, String[])
         */
        OptsBuilder returnGeneratedKeys(String... columns);

        JdbcQueryOptions build();
    }

    /**
     * Batch builder for named SQL.
     */
    interface NamedBatchBuilder {

        /**
         * @return this builder after adding one batch row from named parameter values
         */
        NamedBatchBuilder bind(Map<String, ?> values);

        /**
         * @return this builder after adding one batch row using row binder
         */
        NamedBatchBuilder bind(Consumer<NamedRowBinder> binder);

        <T> NamedBatchBuilder bindAll(Iterable<T> values, JdbcNamedBatchBinder<T> binder);

        /**
         * @return built immutable batch query
         */
        JdbcQueryBatch build();
    }

    /**
     * Batch builder for positional SQL.
     */
    interface TemplateBatchBuilder {

        /**
         * @return this builder after adding one batch row with positional values
         */
        TemplateBatchBuilder bind(@Nullable Object... values);

        /**
         * @return this builder after adding one batch row using row binder
         */
        TemplateBatchBuilder bind(Consumer<TemplateRowBinder> binder);

        <T> TemplateBatchBuilder bindAll(Iterable<T> values, JdbcTemplateBatchBinder<T> binder);

        /**
         * @return built immutable batch query
         */
        JdbcQueryBatch build();
    }

    /**
     * Bound parameter descriptor.
     *
     * @param name   named parameter name, or empty string for positional template parameters
     * @param value  original parameter value
     * @param binder binder that writes this value to a {@link PreparedStatement}
     */
    record Parameter(String name, @Nullable Object value, JdbcParameterBinder binder) {}

    /**
     * Custom binder for a single JDBC placeholder.
     */
    @FunctionalInterface
    interface JdbcParameterBinder {
        void set(PreparedStatement statement, int index) throws SQLException;
    }

    /**
     * Row binder used by named batch builder.
     */
    interface NamedRowBinder {

        /**
         * @return this binder after binding named parameter in current batch row
         */
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

    /**
     * Row binder used by positional batch builder.
     */
    interface TemplateRowBinder {

        /**
         * @return this binder after binding next positional parameter in current batch row
         */
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

    /**
     * SQL-capable named batch binder.
     * <p>
     * Use this when custom row binding code can throw {@link SQLException}.
     *
     * @param <T> source row type
     */
    @FunctionalInterface
    interface JdbcNamedBatchBinder<T> {
        void bind(NamedRowBinder binder, T value) throws SQLException;
    }

    /**
     * SQL-capable positional batch binder.
     * <p>
     * Use this when custom row binding code can throw {@link SQLException}.
     *
     * @param <T> source row type
     */
    @FunctionalInterface
    interface JdbcTemplateBatchBinder<T> {
        void bind(TemplateRowBinder binder, T value) throws SQLException;
    }
}
