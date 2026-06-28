import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.database.flyway {
    requires transitive kora.database.jdbc;
    requires transitive kora.config.common;
    requires transitive org.flywaydb.core;

    exports io.koraframework.database.flyway;
}
