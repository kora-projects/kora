package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.data.SettableByName;
import io.koraframework.database.cassandra.mapper.parameter.CassandraParameterColumnMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Immutable CQL query descriptor that can create a {@link BoundStatement} and bind all parameters.
 * <p>
 * A query stores original CQL, executable Cassandra CQL, statement options, and binders in the order
 * required by Cassandra.
 * <pre>{@code
 * var query = CassandraQuery.named()
 *     .cql("SELECT id, name FROM users WHERE tenant_id = :tenant_id AND id = :id")
 *     .bind("tenant_id", tenantId)
 *     .bind("id", id)
 *     .build();
 * }</pre>
 *
 * @see CassandraExecutor#query(CassandraQuery, java.util.function.Function)
 */
public interface CassandraQuery {

    /**
     * Creates a builder for CQL with named parameters.
     * <p>
     * Use named queries when CQL is easier to read with {@code :name} placeholders. Parameters are
     * bound separately by name, then converted to Cassandra positional placeholders when
     * {@link NamedQueryBuilder#build()} is called. {@link NamedQueryBuilder#bindIn(String, Iterable)}
     * expands values for {@code IN (:ids)} clauses.
     * <pre>{@code
     * var query = CassandraQuery.named()
     *     .cql("SELECT id, name FROM users WHERE tenant_id = :tenant_id")
     *     .bind("tenant_id", tenantId)
     *     .cqlIf(" AND status = :status", status != null)
     *     .bindIf("status", status, status != null)
     *     .cqlIf(" AND id IN (:ids)", !ids.isEmpty())
     *     .bindInIf("ids", ids, !ids.isEmpty())
     *     .build();
     *
     * var users = cassandra.queryList(query, row -> new User(row.getUuid("id"), row.getString("name")));
     * }</pre>
     * CQL identifiers or fragments should be formatted before they are passed to the builder.
     * User values should be passed through {@code bind(...)} or {@code bindIn(...)}.
     *
     * @return named query builder
     */
    static NamedQueryBuilder named() {
        return CassandraQueryImpl.named();
    }

    /**
     * Creates a builder for CQL with positional Cassandra placeholders.
     * <p>
     * Use template queries when CQL already has Cassandra positional placeholders and parameters are
     * bound by position.
     * <pre>{@code
     * var query = CassandraQuery.template()
     *     .cql("SELECT id, name FROM users WHERE tenant_id = ?")
     *     .bind(tenantId)
     *     .cqlIf(" AND status = ?", status != null, status)
     *     .build();
     *
     * var users = cassandra.queryList(query, row -> new User(row.getUuid("id"), row.getString("name")));
     * }</pre>
     * CQL identifiers or fragments should be formatted before they are passed to the builder.
     * User values should be passed through {@code bind(...)}.
     *
     * @return template query builder
     */
    static TemplateBuilder template() {
        return CassandraQueryImpl.template();
    }

    /**
     * Creates a positional query in one call.
     * <p>
     * This is a compact form of {@link #template()} for simple CQL with Cassandra positional
     * placeholders. Arguments are bound in placeholder order.
     * <pre>{@code
     * var query = CassandraQuery.template(
     *     "SELECT id, name FROM users WHERE tenant_id = ? AND id = ?",
     *     tenantId,
     *     id
     * );
     *
     * var users = cassandra.queryList(query, row -> new User(row.getUuid("id"), row.getString("name")));
     * }</pre>
     *
     * @param cql  CQL with positional Cassandra placeholders
     * @param args parameter values in placeholder order
     * @return built query
     */
    static CassandraQuery template(String cql, @Nullable Object... args) {
        return template()
            .cql(cql)
            .bindAll(args)
            .build();
    }

    /** @return original CQL before named parameters are converted */
    String sourceCql();

    /** @return executable CQL with positional placeholders */
    String cql();

    /** @return statement options applied to the bound statement */
    CassandraQueryOptions options();

    /** @return full parameter descriptors in Cassandra binding order */
    List<Parameter> parameters();

    BoundStatement prepare(CqlSession session);

    BoundStatement bind(PreparedStatement statement);

    /**
     * Builder for CQL with named parameters.
     * <p>
     * CQL fragments and parameter values are added separately: {@link #cql(String)} changes CQL,
     * {@link #bind(String, Object)} supplies values. All named parameters used in CQL must be bound,
     * and all bound parameters must be used in CQL.
     * <pre>{@code
     * var query = CassandraQuery.named()
     *     .cql("SELECT id, name FROM users WHERE tenant_id = :tenant_id")
     *     .bind("tenant_id", tenantId)
     *     .cqlIf(" AND status = :status", status != null)
     *     .bindIf("status", status, status != null)
     *     .build();
     * }</pre>
     */
    interface NamedQueryBuilder {

        NamedQueryBuilder cql(String cql);

        default NamedQueryBuilder cqlIf(String cql, boolean condition) {
            return condition
                ? this.cql(cql)
                : this;
        }

        default NamedQueryBuilder opts(Consumer<OptsBuilder> options) {
            var builder = OptsBuilder.builder();
            options.accept(builder);
            return this.opts(builder.build());
        }

        /** @return this builder after setting statement options */
        NamedQueryBuilder opts(CassandraQueryOptions options);

        /**
         * Binds a named parameter value.
         * <pre>{@code
         * CassandraQuery.named()
         *     .cql("SELECT * FROM users WHERE id = :id")
         *     .bind("id", id);
         * }</pre>
         *
         * @return this builder
         */
        NamedQueryBuilder bind(String name, @Nullable Object value);

        <T> NamedQueryBuilder bind(String name, @Nullable T value, CassandraParameterColumnMapper<T> mapper);

        NamedQueryBuilder bind(String name, CassandraParameterBinder parameter);

        /**
         * Binds all map entries as named parameters.
         * <pre>{@code
         * var values = Map.of("tenant_id", tenantId, "status", status);
         *
         * CassandraQuery.named()
         *     .cql("SELECT * FROM users WHERE tenant_id = :tenant_id AND status = :status")
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
         * Binds and expands a named parameter value for an {@code IN (:name)} clause.
         * <pre>{@code
         * CassandraQuery.named()
         *     .cql("SELECT * FROM users WHERE tenant_id = :tenant_id AND id IN (:ids)")
         *     .bind("tenant_id", tenantId)
         *     .bindIn("ids", ids);
         * }</pre>
         *
         * @return this builder
         */
        NamedQueryBuilder bindIn(String name, Iterable<?> values);

        <T> NamedQueryBuilder bindIn(String name, Iterable<T> values, CassandraParameterColumnMapper<T> mapper);

        /**
         * Binds a named parameter only when condition is true.
         * <pre>{@code
         * CassandraQuery.named()
         *     .cql("SELECT * FROM users WHERE tenant_id = :tenant_id")
         *     .bind("tenant_id", tenantId)
         *     .cqlIf(" AND status = :status", status != null)
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

        default <T> NamedQueryBuilder bindIf(String name, @Nullable T value, CassandraParameterColumnMapper<T> mapper, boolean condition) {
            return condition
                ? this.bind(name, value, mapper)
                : this;
        }

        default NamedQueryBuilder bindIf(String name, CassandraParameterBinder parameter, boolean condition) {
            return condition
                ? this.bind(name, parameter)
                : this;
        }

        default NamedQueryBuilder bindInIf(String name, Iterable<?> values, boolean condition) {
            return condition
                ? this.bindIn(name, values)
                : this;
        }

        default <T> NamedQueryBuilder bindInIf(String name, Iterable<T> values, CassandraParameterColumnMapper<T> mapper, boolean condition) {
            return condition
                ? this.bindIn(name, values, mapper)
                : this;
        }

        /** @return built immutable named query */
        CassandraQuery build();
    }

    /**
     * Builder for CQL with positional Cassandra placeholders.
     * <pre>{@code
     * var query = CassandraQuery.template()
     *     .cql("SELECT id, name FROM users WHERE tenant_id = ?")
     *     .bind(tenantId)
     *     .cqlIf(" AND status = ?", status != null, status)
     *     .build();
     * }</pre>
     */
    interface TemplateBuilder {

        TemplateBuilder cql(String cql);

        default TemplateBuilder cqlIf(String cql, boolean condition) {
            return condition
                ? this.cql(cql)
                : this;
        }

        /**
         * Appends CQL and binds positional values only when condition is true.
         * <pre>{@code
         * CassandraQuery.template()
         *     .cql("SELECT * FROM users WHERE tenant_id = ?")
         *     .bind(tenantId)
         *     .cqlIf(" AND status = ?", status != null, status);
         * }</pre>
         *
         * @return this builder
         */
        default TemplateBuilder cqlIf(String cql, boolean condition, @Nullable Object... args) {
            if (condition) {
                this.cql(cql);
                this.bindAll(args);
            }
            return this;
        }

        default TemplateBuilder opts(Consumer<OptsBuilder> options) {
            var builder = OptsBuilder.builder();
            options.accept(builder);
            return this.opts(builder.build());
        }

        /** @return this builder after setting statement options */
        TemplateBuilder opts(CassandraQueryOptions options);

        /**
         * Binds next positional parameter value.
         * <pre>{@code
         * CassandraQuery.template()
         *     .cql("SELECT * FROM users WHERE id = ?")
         *     .bind(id);
         * }</pre>
         *
         * @return this builder
         */
        TemplateBuilder bind(@Nullable Object value);

        <T> TemplateBuilder bind(@Nullable T value, CassandraParameterColumnMapper<T> mapper);

        TemplateBuilder bind(CassandraParameterBinder parameter);

        /**
         * Binds values in placeholder order.
         * <pre>{@code
         * CassandraQuery.template()
         *     .cql("SELECT * FROM users WHERE tenant_id = ? AND status = ?")
         *     .bindAll(tenantId, status);
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

        /** @return built immutable template query */
        CassandraQuery build();
    }

    interface OptsBuilder {

        static OptsBuilder builder() {
            return CassandraQueryImpl.opts();
        }

        /**
         * @return this builder after setting request consistency level
         * @see BoundStatementBuilder#setConsistencyLevel(ConsistencyLevel)
         */
        OptsBuilder consistencyLevel(ConsistencyLevel consistencyLevel);

        /**
         * @return this builder after setting request serial consistency level
         * @see BoundStatementBuilder#setSerialConsistencyLevel(ConsistencyLevel)
         */
        OptsBuilder serialConsistencyLevel(ConsistencyLevel consistencyLevel);

        /**
         * @return this builder after setting request page size
         * @see BoundStatementBuilder#setPageSize(int)
         */
        OptsBuilder pageSize(int pageSize);

        /**
         * @return this builder after setting request timeout
         * @see BoundStatementBuilder#setTimeout(Duration)
         */
        OptsBuilder timeout(Duration timeout);

        /**
         * @return this builder after setting request idempotence flag
         * @see BoundStatementBuilder#setIdempotence(Boolean)
         */
        OptsBuilder idempotent(boolean idempotent);

        /**
         * @return this builder after enabling or disabling request tracing
         * @see BoundStatementBuilder#setTracing(boolean)
         */
        OptsBuilder tracing(boolean tracing);

        /** @return built immutable statement options */
        CassandraQueryOptions build();
    }

    /**
     * Bound parameter descriptor.
     *
     * @param name   named parameter name, or empty string for positional template parameters
     * @param value  original parameter value
     * @param binder binder that writes this value to a {@link BoundStatement}
     */
    record Parameter(String name, @Nullable Object value, CassandraParameterBinder binder) {}

    /**
     * Custom binder for a single Cassandra placeholder.
     */
    @FunctionalInterface
    interface CassandraParameterBinder {
        void set(SettableByName<?> statement, int index);
    }
}
