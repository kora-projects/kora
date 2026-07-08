package io.koraframework.database.cassandra.impl;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import io.koraframework.database.cassandra.CassandraQuery;
import io.koraframework.database.cassandra.CassandraQueryOptions;
import io.koraframework.database.cassandra.mapper.parameter.CassandraParameterColumnMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CassandraQueryImpl implements CassandraQuery {

    private final String sourceCql;
    private final String cql;
    private final List<Parameter> parameters;
    private final CassandraQueryOptions options;

    private CassandraQueryImpl(String sourceCql, String cql, List<Parameter> parameters, CassandraQueryOptions options) {
        this.sourceCql = sourceCql;
        this.cql = cql;
        this.parameters = List.copyOf(parameters);
        this.options = Objects.requireNonNull(options);
    }

    public static NamedQueryBuilder named() {
        return new NamedQueryBuilderImpl();
    }

    public static TemplateBuilder template() {
        return new TemplateQueryBuilderImpl();
    }

    public static OptsBuilder opts() {
        return new OptsBuilderImpl();
    }

    @Override
    public String sourceCql() {
        return this.sourceCql;
    }

    @Override
    public String cql() {
        return this.cql;
    }

    @Override
    public CassandraQueryOptions options() {
        return this.options;
    }

    @Override
    public List<Parameter> parameters() {
        return this.parameters;
    }

    @Override
    public BoundStatement prepare(com.datastax.oss.driver.api.core.CqlSession session) {
        return this.bind(session.prepare(this.cql));
    }

    @Override
    public BoundStatement bind(PreparedStatement statement) {
        var builder = statement.boundStatementBuilder();
        applyOptions(builder, this.options);
        for (int i = 0; i < this.parameters.size(); i++) {
            this.parameters.get(i).binder().set(builder, i);
        }
        return builder.build();
    }

    private static void applyOptions(BoundStatementBuilder builder, CassandraQueryOptions options) {
        if (options.consistencyLevel() != null) {
            builder.setConsistencyLevel(options.consistencyLevel());
        }
        if (options.serialConsistencyLevel() != null) {
            builder.setSerialConsistencyLevel(options.serialConsistencyLevel());
        }
        if (options.pageSize() != null) {
            builder.setPageSize(options.pageSize());
        }
        if (options.timeout() != null) {
            builder.setTimeout(options.timeout());
        }
        if (options.idempotent() != null) {
            builder.setIdempotence(options.idempotent());
        }
        if (options.tracing() != null) {
            builder.setTracing(options.tracing());
        }
    }

    private static final class OptsBuilderImpl implements OptsBuilder {
        @Nullable
        private ConsistencyLevel consistencyLevel;
        @Nullable
        private ConsistencyLevel serialConsistencyLevel;
        @Nullable
        private Integer pageSize;
        @Nullable
        private Duration timeout;
        @Nullable
        private Boolean idempotent;
        @Nullable
        private Boolean tracing;

        @Override
        public OptsBuilder consistencyLevel(ConsistencyLevel consistencyLevel) {
            this.consistencyLevel = Objects.requireNonNull(consistencyLevel);
            return this;
        }

        @Override
        public OptsBuilder serialConsistencyLevel(ConsistencyLevel consistencyLevel) {
            this.serialConsistencyLevel = Objects.requireNonNull(consistencyLevel);
            return this;
        }

        @Override
        public OptsBuilder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        @Override
        public OptsBuilder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
            return this;
        }

        @Override
        public OptsBuilder idempotent(boolean idempotent) {
            this.idempotent = idempotent;
            return this;
        }

        @Override
        public OptsBuilder tracing(boolean tracing) {
            this.tracing = tracing;
            return this;
        }

        @Override
        public CassandraQueryOptions build() {
            return new CassandraQueryOptionsImpl(
                this.consistencyLevel,
                this.serialConsistencyLevel,
                this.pageSize,
                this.timeout,
                this.idempotent,
                this.tracing
            );
        }
    }

    private static final class NamedQueryBuilderImpl implements NamedQueryBuilder {

        private final StringBuilder cql = new StringBuilder();
        private final Map<String, ParameterValue> params = new LinkedHashMap<>();
        private CassandraQueryOptions options = CassandraQueryOptions.DEFAULT;

        @Override
        public NamedQueryBuilder cql(String cql) {
            this.cql.append(Objects.requireNonNull(cql));
            return this;
        }

        @Override
        public NamedQueryBuilder opts(CassandraQueryOptions options) {
            this.options = Objects.requireNonNull(options);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public NamedQueryBuilder bind(String name, @Nullable Object value) {
            Objects.requireNonNull(name);
            this.params.put(name, new ParameterValue(Collections.singletonList(value), false, item -> (statement, index) -> {
                if (item == null) {
                    statement.setToNull(index);
                } else {
                    statement.set(index, item, (Class<Object>) item.getClass());
                }
            }));
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> NamedQueryBuilder bind(String name, @Nullable T value, CassandraParameterColumnMapper<T> mapper) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(mapper);
            this.params.put(name, new ParameterValue(Collections.singletonList(value), false, item -> (statement, index) -> mapper.apply(statement, index, (T) item)));
            return this;
        }

        @Override
        public NamedQueryBuilder bind(String name, CassandraParameterBinder parameter) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(parameter);
            this.params.put(name, new ParameterValue(Collections.singletonList(null), false, item -> parameter));
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public NamedQueryBuilder bindIn(String name, Iterable<?> values) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(values);
            this.params.put(name, new ParameterValue(values(values), true, item -> (statement, index) -> {
                if (item == null) {
                    statement.setToNull(index);
                } else {
                    statement.set(index, item, (Class<Object>) item.getClass());
                }
            }));
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> NamedQueryBuilder bindIn(String name, Iterable<T> values, CassandraParameterColumnMapper<T> mapper) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(values);
            Objects.requireNonNull(mapper);
            this.params.put(name, new ParameterValue(values(values), true, item -> (statement, index) -> mapper.apply(statement, index, (T) item)));
            return this;
        }

        @Override
        public CassandraQuery build() {
            var sourceCql = this.cql.toString();
            var parsed = parse(sourceCql, this.params);
            return new CassandraQueryImpl(sourceCql, parsed.cql(), parsed.parameters(), this.options);
        }
    }

    private static final class TemplateQueryBuilderImpl implements TemplateBuilder {
        private final StringBuilder cql = new StringBuilder();
        private final List<Parameter> parameters = new ArrayList<>();
        private CassandraQueryOptions options = CassandraQueryOptions.DEFAULT;

        @Override
        public TemplateBuilder cql(String cql) {
            this.cql.append(Objects.requireNonNull(cql));
            return this;
        }

        @Override
        public TemplateBuilder opts(CassandraQueryOptions options) {
            this.options = Objects.requireNonNull(options);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TemplateBuilder bind(@Nullable Object value) {
            this.parameters.add(templateParameter(value, item -> (statement, index) -> {
                if (item == null) {
                    statement.setToNull(index);
                } else {
                    statement.set(index, item, (Class<Object>) item.getClass());
                }
            }));
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> TemplateBuilder bind(@Nullable T value, CassandraParameterColumnMapper<T> mapper) {
            Objects.requireNonNull(mapper);
            this.parameters.add(templateParameter(value, item -> (statement, index) -> mapper.apply(statement, index, (T) item)));
            return this;
        }

        @Override
        public TemplateBuilder bind(CassandraParameterBinder parameter) {
            Objects.requireNonNull(parameter);
            this.parameters.add(new Parameter("", null, parameter));
            return this;
        }

        @Override
        public CassandraQuery build() {
            var sourceCql = this.cql.toString();
            return new CassandraQueryImpl(sourceCql, sourceCql, this.parameters, this.options);
        }
    }

    private static ParsedQuery parse(String sourceCql, Map<String, ParameterValue> params) {
        var cql = new StringBuilder(sourceCql.length());
        var parameters = new ArrayList<Parameter>();
        var usedParams = new ArrayList<String>();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;

        for (int i = 0; i < sourceCql.length(); i++) {
            char c = sourceCql.charAt(i);
            if (c == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
                cql.append(c);
                continue;
            }
            if (c == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                cql.append(c);
                continue;
            }
            if (c != ':' || singleQuoted || doubleQuoted) {
                cql.append(c);
                continue;
            }
            if (i + 1 < sourceCql.length() && sourceCql.charAt(i + 1) == ':') {
                cql.append("::");
                i++;
                continue;
            }
            if (i > 0 && sourceCql.charAt(i - 1) == ':') {
                cql.append(c);
                continue;
            }

            int nameStart = i + 1;
            if (nameStart >= sourceCql.length() || !isNameStart(sourceCql.charAt(nameStart))) {
                cql.append(c);
                continue;
            }

            int nameEnd = nameStart + 1;
            while (nameEnd < sourceCql.length() && isNamePart(sourceCql.charAt(nameEnd))) {
                nameEnd++;
            }

            var name = sourceCql.substring(nameStart, nameEnd);
            if (!params.containsKey(name)) {
                throw new IllegalArgumentException("Parameter '%s' is not specified".formatted(name));
            }
            usedParams.add(name);
            appendParameter(cql, parameters, name, params.get(name));
            i = nameEnd - 1;
        }

        for (var param : params.keySet()) {
            if (!usedParams.contains(param)) {
                throw new IllegalArgumentException("Parameter '%s' is not used in CQL".formatted(param));
            }
        }

        return new ParsedQuery(cql.toString(), parameters);
    }

    private static void appendParameter(StringBuilder cql, List<Parameter> parameters, String name, ParameterValue value) {
        var values = value.values();
        if (!value.expandable()) {
            cql.append('?');
            var parameterValue = values.get(0);
            parameters.add(new Parameter(name, parameterValue, value.binder(parameterValue)));
            return;
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Parameter '%s' collection is empty".formatted(name));
        }
        cql.append(String.join(", ", Collections.nCopies(values.size(), "?")));
        for (var item : values) {
            parameters.add(new Parameter(name, item, value.binder(item)));
        }
    }

    private static List<Object> values(Iterable<?> values) {
        var result = new ArrayList<>();
        for (var value : values) {
            result.add(value);
        }
        return result;
    }

    private static Parameter templateParameter(@Nullable Object value, BinderFactory binderFactory) {
        return new Parameter("", value, binderFactory.create(value));
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private record ParameterValue(List<?> values, boolean expandable, BinderFactory binderFactory) {
        CassandraParameterBinder binder(@Nullable Object value) {
            return this.binderFactory.create(value);
        }
    }

    private interface BinderFactory {
        CassandraParameterBinder create(@Nullable Object value);
    }

    private record ParsedQuery(String cql, List<Parameter> parameters) {}

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CassandraQuery cassandraQuery)) {
            return false;
        }
        return Objects.equals(this.cql, cassandraQuery.cql())
            && Objects.equals(this.parameters(), cassandraQuery.parameters())
            && Objects.equals(this.options, cassandraQuery.options());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.cql, this.parameters, this.options);
    }

    @Override
    public String toString() {
        return "CassandraQuery{"
            + "sourceCql='" + this.sourceCql + '\''
            + ", cql='" + this.cql + '\''
            + ", parameters=" + this.parameters.stream()
                .map(Parameter::value)
                .toList()
            + ", options=" + this.options
            + '}';
    }
}
