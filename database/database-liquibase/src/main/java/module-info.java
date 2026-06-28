import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.database.liquibase {
    requires transitive kora.database.jdbc;
    requires transitive kora.config.common;
    requires transitive liquibase.core;

    exports io.koraframework.database.liquibase;
}
