package io.koraframework.database.jdbc;

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLType;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <b>Русский</b>: Неизменяемое описание JDBC запроса с именованными параметрами, которое умеет создавать
 * {@link PreparedStatement} и проставлять все параметры в правильном порядке.
 * <hr>
 * <b>English</b>: An immutable JDBC query descriptor with named parameters that can create a {@link PreparedStatement}
 * and bind all parameters in the correct order.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * var query = JdbcQuery.builder()
 *     .sql("SELECT * FROM users WHERE 1 = 1")
 *     .bindIf(" AND status = :status", "status", status, status != null)
 *     .bind(" AND id IN (:ids)", "ids", List.of(1L, 2L, 3L))
 *     .build();
 *
 * try (var statement = query.prepare(connection)) {
 *     try (var rs = statement.executeQuery()) {
 *         // read result set
 *     }
 * }
 * }
 * </pre>
 *
 * @see JdbcExecutor#query(JdbcQuery, JdbcExecutor.SqlFunction)
 */
public final class JdbcQuery {

    private final String sourceSql;
    private final String sql;
    private final List<Parameter> parameters;

    private JdbcQuery(String sourceSql, String sql, List<Parameter> parameters) {
        this.sourceSql = sourceSql;
        this.sql = sql;
        this.parameters = List.copyOf(parameters);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * <b>Русский</b>: Возвращает исходный SQL с именованными параметрами.
     * <hr>
     * <b>English</b>: Returns the original SQL with named parameters.
     *
     * @return исходный SQL / original SQL
     */
    public String sourceSql() {
        return this.sourceSql;
    }

    /**
     * <b>Русский</b>: Возвращает JDBC SQL, где именованные параметры заменены на {@code ?}.
     * <hr>
     * <b>English</b>: Returns JDBC SQL where named parameters are replaced with {@code ?}.
     *
     * @return JDBC SQL
     */
    public String sql() {
        return this.sql;
    }

    /**
     * <b>Русский</b>: Возвращает значения параметров в порядке их привязки к {@link PreparedStatement}.
     * <hr>
     * <b>English</b>: Returns parameter values in the order they are bound to a {@link PreparedStatement}.
     *
     * @return значения параметров / parameter values
     */
    public List<Object> parameterValues() {
        return this.parameters.stream()
            .map(Parameter::value)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * <b>Русский</b>: Создает {@link PreparedStatement} из переданного соединения и проставляет все параметры.
     * <hr>
     * <b>English</b>: Creates a {@link PreparedStatement} from the provided connection and binds all parameters.
     *
     * @param connection JDBC соединение / JDBC connection
     * @return подготовленный запрос с параметрами / prepared statement with bound parameters
     * @throws UncheckedSqlException если JDBC драйвер вернул ошибку / when the JDBC driver returns an error
     */
    public PreparedStatement prepare(Connection connection) throws UncheckedSqlException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(this.sql);
            for (int i = 0; i < this.parameters.size(); i++) {
                this.parameters.get(i).binder().set(statement, i + 1);
            }
            return statement;
        } catch (SQLException e) {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw new UncheckedSqlException(e);
        }
    }

    @FunctionalInterface
    public interface JdbcParameter {
        void set(PreparedStatement statement, int index) throws SQLException;
    }

    private record Parameter(String name, @Nullable Object value, JdbcParameter binder) {}

    public static final class Builder {

        private final StringBuilder sql = new StringBuilder();
        private final Map<String, ParameterValue> params = new LinkedHashMap<>();

        private Builder() {}

        /**
         * <b>Русский</b>: Добавляет фрагмент SQL без параметров.
         * <hr>
         * <b>English</b>: Appends a SQL fragment without parameters.
         *
         * @param sql SQL фрагмент / SQL fragment
         * @return этот билдер / this builder
         */
        public Builder sql(String sql) {
            this.sql.append(Objects.requireNonNull(sql));
            return this;
        }

        /**
         * <b>Русский</b>: Добавляет фрагмент SQL только если условие истинно.
         * <hr>
         * <b>English</b>: Appends a SQL fragment only when the condition is true.
         *
         * @param sql       SQL фрагмент / SQL fragment
         * @param condition условие добавления / append condition
         * @return этот билдер / this builder
         */
        public Builder sqlIf(String sql, boolean condition) {
            if (condition) {
                this.sql(sql);
            }
            return this;
        }

        /**
         * <b>Русский</b>: Добавляет значение именованного параметра.
         * <hr>
         * <b>English</b>: Adds a named parameter value.
         *
         * @param name  имя параметра без {@code :} / parameter name without {@code :}
         * @param value значение параметра / parameter value
         * @return этот билдер / this builder
         */
        public Builder param(String name, @Nullable Object value) {
            Objects.requireNonNull(name);
            this.params.put(name, new ParameterValue(value, true, item -> (statement, index) -> statement.setObject(index, item)));
            return this;
        }

        public Builder param(String name, @Nullable Object value, int sqlType) {
            Objects.requireNonNull(name);
            this.params.put(name, new ParameterValue(value, true, item -> (statement, index) -> {
                if (item == null) {
                    statement.setNull(index, sqlType);
                } else {
                    statement.setObject(index, item, sqlType);
                }
            }));
            return this;
        }

        public Builder param(String name, @Nullable Object value, SQLType sqlType) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(sqlType);
            this.params.put(name, new ParameterValue(value, true, item -> (statement, index) -> statement.setObject(index, item, sqlType)));
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> Builder param(String name, @Nullable T value, JdbcParameterColumnMapper<T> mapper) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(mapper);
            this.params.put(name, new ParameterValue(value, true, item -> (statement, index) -> mapper.set(statement, index, (T) item)));
            return this;
        }

        public Builder param(String name, JdbcParameter parameter) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(parameter);
            this.params.put(name, new ParameterValue(null, false, item -> parameter));
            return this;
        }

        /**
         * <b>Русский</b>: Добавляет значение именованного параметра только если условие истинно.
         * <hr>
         * <b>English</b>: Adds a named parameter value only when the condition is true.
         *
         * @param name      имя параметра без {@code :} / parameter name without {@code :}
         * @param value     значение параметра / parameter value
         * @param condition условие добавления / append condition
         * @return этот билдер / this builder
         */
        public Builder paramIf(String name, @Nullable Object value, boolean condition) {
            if (condition) {
                this.param(name, value);
            }
            return this;
        }

        public Builder paramIf(String name, @Nullable Object value, int sqlType, boolean condition) {
            if (condition) {
                this.param(name, value, sqlType);
            }
            return this;
        }

        public Builder paramIf(String name, @Nullable Object value, SQLType sqlType, boolean condition) {
            if (condition) {
                this.param(name, value, sqlType);
            }
            return this;
        }

        public <T> Builder paramIf(String name, @Nullable T value, JdbcParameterColumnMapper<T> mapper, boolean condition) {
            if (condition) {
                this.param(name, value, mapper);
            }
            return this;
        }

        public Builder paramIf(String name, JdbcParameter parameter, boolean condition) {
            if (condition) {
                this.param(name, parameter);
            }
            return this;
        }

        /**
         * <b>Русский</b>: Добавляет SQL фрагмент и значение параметра одним вызовом.
         * <hr>
         * <b>English</b>: Appends a SQL fragment and adds a parameter value in one call.
         *
         * @param sql   SQL фрагмент с именованным параметром / SQL fragment with a named parameter
         * @param name  имя параметра без {@code :} / parameter name without {@code :}
         * @param value значение параметра / parameter value
         * @return этот билдер / this builder
         */
        public Builder bind(String sql, String name, @Nullable Object value) {
            return this.sql(sql).param(name, value);
        }

        public Builder bind(String sql, String name, @Nullable Object value, int sqlType) {
            return this.sql(sql).param(name, value, sqlType);
        }

        public Builder bind(String sql, String name, @Nullable Object value, SQLType sqlType) {
            return this.sql(sql).param(name, value, sqlType);
        }

        public <T> Builder bind(String sql, String name, @Nullable T value, JdbcParameterColumnMapper<T> mapper) {
            return this.sql(sql).param(name, value, mapper);
        }

        public Builder bind(String sql, String name, JdbcParameter parameter) {
            return this.sql(sql).param(name, parameter);
        }

        /**
         * <b>Русский</b>: Добавляет SQL фрагмент и значение параметра одним вызовом только если условие истинно.
         * <hr>
         * <b>English</b>: Appends a SQL fragment and adds a parameter value in one call only when the condition is true.
         *
         * @param sql       SQL фрагмент с именованным параметром / SQL fragment with a named parameter
         * @param name      имя параметра без {@code :} / parameter name without {@code :}
         * @param value     значение параметра / parameter value
         * @param condition условие добавления / append condition
         * @return этот билдер / this builder
         */
        public Builder bindIf(String sql, String name, @Nullable Object value, boolean condition) {
            if (condition) {
                this.bind(sql, name, value);
            }
            return this;
        }

        public Builder bindIf(String sql, String name, @Nullable Object value, int sqlType, boolean condition) {
            if (condition) {
                this.bind(sql, name, value, sqlType);
            }
            return this;
        }

        public Builder bindIf(String sql, String name, @Nullable Object value, SQLType sqlType, boolean condition) {
            if (condition) {
                this.bind(sql, name, value, sqlType);
            }
            return this;
        }

        public <T> Builder bindIf(String sql, String name, @Nullable T value, JdbcParameterColumnMapper<T> mapper, boolean condition) {
            if (condition) {
                this.bind(sql, name, value, mapper);
            }
            return this;
        }

        public Builder bindIf(String sql, String name, JdbcParameter parameter, boolean condition) {
            if (condition) {
                this.bind(sql, name, parameter);
            }
            return this;
        }

        /**
         * <b>Русский</b>: Создает неизменяемый {@link JdbcQuery}, заменяя именованные параметры на JDBC плейсхолдеры.
         * Коллекции и массивы, кроме {@code byte[]}, раскрываются в несколько плейсхолдеров для {@code IN (:ids)}.
         * <hr>
         * <b>English</b>: Creates an immutable {@link JdbcQuery}, replacing named parameters with JDBC placeholders.
         * Collections and arrays, except {@code byte[]}, are expanded into multiple placeholders for {@code IN (:ids)}.
         *
         * @return готовый JDBC запрос / built JDBC query
         */
        public JdbcQuery build() {
            var sourceSql = this.sql.toString();
            var parsed = parse(sourceSql, this.params);
            return new JdbcQuery(sourceSql, parsed.sql(), parsed.parameters());
        }

        private static ParsedQuery parse(String sourceSql, Map<String, ParameterValue> params) {
            var sql = new StringBuilder(sourceSql.length());
            var parameters = new ArrayList<Parameter>();
            var usedParams = new ArrayList<String>();
            boolean singleQuoted = false;
            boolean doubleQuoted = false;

            for (int i = 0; i < sourceSql.length(); i++) {
                char c = sourceSql.charAt(i);
                if (c == '\'' && !doubleQuoted) {
                    singleQuoted = !singleQuoted;
                    sql.append(c);
                    continue;
                }
                if (c == '"' && !singleQuoted) {
                    doubleQuoted = !doubleQuoted;
                    sql.append(c);
                    continue;
                }
                if (c != ':' || singleQuoted || doubleQuoted) {
                    sql.append(c);
                    continue;
                }
                if (i + 1 < sourceSql.length() && sourceSql.charAt(i + 1) == ':') {
                    sql.append("::");
                    i++;
                    continue;
                }
                if (i > 0 && sourceSql.charAt(i - 1) == ':') {
                    sql.append(c);
                    continue;
                }

                int nameStart = i + 1;
                if (nameStart >= sourceSql.length() || !isNameStart(sourceSql.charAt(nameStart))) {
                    sql.append(c);
                    continue;
                }

                int nameEnd = nameStart + 1;
                while (nameEnd < sourceSql.length() && isNamePart(sourceSql.charAt(nameEnd))) {
                    nameEnd++;
                }

                var name = sourceSql.substring(nameStart, nameEnd);
                if (!params.containsKey(name)) {
                    throw new IllegalArgumentException("Parameter '%s' is not specified".formatted(name));
                }
                usedParams.add(name);
                appendParameter(sql, parameters, name, params.get(name));
                i = nameEnd - 1;
            }

            for (var param : params.keySet()) {
                if (!usedParams.contains(param)) {
                    throw new IllegalArgumentException("Parameter '%s' is not used in SQL".formatted(param));
                }
            }

            return new ParsedQuery(sql.toString(), parameters);
        }

        private static void appendParameter(StringBuilder sql, List<Parameter> parameters, String name, ParameterValue value) {
            var values = value.expandable()
                ? expand(value.value())
                : null;
            if (values == null) {
                sql.append('?');
                parameters.add(new Parameter(name, value.value(), value.binder(value.value())));
                return;
            }
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Parameter '%s' collection is empty".formatted(name));
            }
            sql.append(String.join(", ", Collections.nCopies(values.size(), "?")));
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
        JdbcParameter binder(@Nullable Object value) {
            return this.binderFactory.create(value);
        }
    }

    private interface BinderFactory {
        JdbcParameter create(@Nullable Object value);
    }

    private record ParsedQuery(String sql, List<Parameter> parameters) {}

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JdbcQuery jdbcQuery)) return false;
        return Objects.equals(sql, jdbcQuery.sql) && Objects.equals(parameterValues(), jdbcQuery.parameterValues());
    }

    @Override
    public int hashCode() {
        return Objects.hash(sql, parameterValues());
    }

    @Override
    public String toString() {
        return sourceSql;
    }
}
