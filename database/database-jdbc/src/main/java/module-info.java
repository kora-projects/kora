import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.database.jdbc {
    requires transitive java.sql;
    requires transitive kora.common;
    requires transitive kora.database.common;
    requires transitive kora.config.common;
    requires transitive com.zaxxer.hikari;

    exports io.koraframework.database.jdbc;
    exports io.koraframework.database.jdbc.mapper.parameter;
    exports io.koraframework.database.jdbc.mapper.result;
}
