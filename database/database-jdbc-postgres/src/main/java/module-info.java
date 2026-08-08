import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.database.jdbc.postgres {
    requires transitive java.sql;
    requires transitive kora.common;
    requires transitive kora.database.jdbc;
    requires transitive kora.json.common;
    requires transitive org.postgresql.jdbc;

    exports io.koraframework.database.jdbc.postgres;
    exports io.koraframework.database.jdbc.postgres.annotation;
    exports io.koraframework.database.jdbc.postgres.mapper.parameter;
    exports io.koraframework.database.jdbc.postgres.mapper.result;
}
