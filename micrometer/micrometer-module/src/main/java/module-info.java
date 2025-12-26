import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.micrometer.module {
    requires transitive kora.common;
    requires static kora.resilent.kora;
}
