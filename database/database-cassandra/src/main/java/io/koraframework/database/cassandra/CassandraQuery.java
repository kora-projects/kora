package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.data.SettableByName;
import io.koraframework.database.cassandra.mapper.parameter.CassandraParameterColumnMapper;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <b>Русский</b>: Неизменяемое описание CQL запроса с именованными параметрами, которое умеет создавать
 * {@link BoundStatement} из {@link PreparedStatement} и проставлять все параметры в правильном порядке.
 * <hr>
 * <b>English</b>: An immutable CQL query descriptor with named parameters that can create a {@link BoundStatement}
 * from a {@link PreparedStatement} and bind all parameters in the correct order.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * var query = CassandraQuery.builder()
 *     .sql("SELECT * FROM users WHERE tenant_id = :tenant_id")
 *     .bindIf(" AND status = :status", "status", status, status != null)
 *     .build();
 *
 * var statement = query.prepare(session);
 * var resultSet = session.execute(statement);
 * }
 * </pre>
 *
 * @see CassandraExecutor#query(CassandraQuery, java.util.function.Function)
 */
public final class CassandraQuery {

    private final String sourceCql;
    private final String cql;
    private final List<Parameter> parameters;

    private CassandraQuery(String sourceCql, String cql, List<Parameter> parameters) {
        this.sourceCql = sourceCql;
        this.cql = cql;
        this.parameters = List.copyOf(parameters);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String sourceCql() {
        return this.sourceCql;
    }

    public String cql() {
        return this.cql;
    }

    public List<Object> parameterValues() {
        return this.parameters.stream()
            .map(Parameter::value)
            .collect(Collectors.toUnmodifiableList());
    }

    public BoundStatement prepare(CqlSession session) {
        return this.bind(session.prepare(this.cql));
    }

    public BoundStatement bind(PreparedStatement statement) {
        var builder = statement.boundStatementBuilder();
        for (int i = 0; i < this.parameters.size(); i++) {
            this.parameters.get(i).binder().set(builder, i);
        }
        return builder.build();
    }

    @FunctionalInterface
    public interface CassandraParameter {
        void set(SettableByName<?> statement, int index);
    }

    private record Parameter(String name, @Nullable Object value, CassandraParameter binder) {}

    public static final class Builder {

        private final StringBuilder cql = new StringBuilder();
        private final Map<String, ParameterValue> params = new LinkedHashMap<>();

        private Builder() {}

        public Builder sql(String cql) {
            this.cql.append(Objects.requireNonNull(cql));
            return this;
        }

        public Builder sqlIf(String cql, boolean condition) {
            if (condition) {
                this.sql(cql);
            }
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder param(String name, @Nullable Object value) {
            Objects.requireNonNull(name);
            this.params.put(name, new ParameterValue(value, true, item -> (statement, index) -> {
                if (item == null) {
                    statement.setToNull(index);
                } else {
                    statement.set(index, item, (Class<Object>) item.getClass());
                }
            }));
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> Builder param(String name, @Nullable T value, CassandraParameterColumnMapper<T> mapper) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(mapper);
            this.params.put(name, new ParameterValue(value, true, item -> (statement, index) -> mapper.apply(statement, index, (T) item)));
            return this;
        }

        public Builder param(String name, CassandraParameter parameter) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(parameter);
            this.params.put(name, new ParameterValue(null, false, item -> parameter));
            return this;
        }

        public Builder paramIf(String name, @Nullable Object value, boolean condition) {
            if (condition) {
                this.param(name, value);
            }
            return this;
        }

        public <T> Builder paramIf(String name, @Nullable T value, CassandraParameterColumnMapper<T> mapper, boolean condition) {
            if (condition) {
                this.param(name, value, mapper);
            }
            return this;
        }

        public Builder paramIf(String name, CassandraParameter parameter, boolean condition) {
            if (condition) {
                this.param(name, parameter);
            }
            return this;
        }

        public Builder bind(String cql, String name, @Nullable Object value) {
            return this.sql(cql).param(name, value);
        }

        public <T> Builder bind(String cql, String name, @Nullable T value, CassandraParameterColumnMapper<T> mapper) {
            return this.sql(cql).param(name, value, mapper);
        }

        public Builder bind(String cql, String name, CassandraParameter parameter) {
            return this.sql(cql).param(name, parameter);
        }

        public Builder bindIf(String cql, String name, @Nullable Object value, boolean condition) {
            if (condition) {
                this.bind(cql, name, value);
            }
            return this;
        }

        public <T> Builder bindIf(String cql, String name, @Nullable T value, CassandraParameterColumnMapper<T> mapper, boolean condition) {
            if (condition) {
                this.bind(cql, name, value, mapper);
            }
            return this;
        }

        public Builder bindIf(String cql, String name, CassandraParameter parameter, boolean condition) {
            if (condition) {
                this.bind(cql, name, parameter);
            }
            return this;
        }

        public CassandraQuery build() {
            var sourceCql = this.cql.toString();
            var parsed = parse(sourceCql, this.params);
            return new CassandraQuery(sourceCql, parsed.cql(), parsed.parameters());
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
            var values = value.expandable()
                ? expand(value.value())
                : null;
            if (values == null) {
                cql.append('?');
                parameters.add(new Parameter(name, value.value(), value.binder(value.value())));
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

        @Nullable
        private static List<?> expand(@Nullable Object value) {
            if (value instanceof Collection<?> collection) {
                return new ArrayList<>(collection);
            }
            if (value != null && value.getClass().isArray() && !(value instanceof byte[])) {
                var result = new ArrayList<>();
                for (int i = 0; i < Array.getLength(value); i++) {
                    result.add(Array.get(value, i));
                }
                return result;
            }
            return null;
        }

        private static boolean isNameStart(char c) {
            return Character.isLetter(c) || c == '_';
        }

        private static boolean isNamePart(char c) {
            return Character.isLetterOrDigit(c) || c == '_';
        }
    }

    private record ParameterValue(@Nullable Object value, boolean expandable, BinderFactory binderFactory) {
        CassandraParameter binder(@Nullable Object value) {
            return this.binderFactory.create(value);
        }
    }

    private interface BinderFactory {
        CassandraParameter create(@Nullable Object value);
    }

    private record ParsedQuery(String cql, List<Parameter> parameters) {}

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CassandraQuery that)) return false;
        return Objects.equals(cql, that.cql) && Objects.equals(parameterValues(), that.parameterValues());
    }

    @Override
    public int hashCode() {
        return Objects.hash(cql, parameterValues());
    }

    @Override
    public String toString() {
        return sourceCql;
    }
}
