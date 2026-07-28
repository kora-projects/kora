import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.database.jdbc.hikari {
    requires kora.database.jdbc.common;

    requires transitive com.zaxxer.hikari;

    exports io.koraframework.database.jdbc.hikari;
}
