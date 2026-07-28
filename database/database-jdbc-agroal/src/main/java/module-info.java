import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.database.jdbc.agroal {
    requires kora.database.jdbc.common;

    requires transitive io.agroal.pool;

    exports io.koraframework.database.jdbc.agroal;
}
