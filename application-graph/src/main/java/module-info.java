import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.application.graph {
    exports ru.tinkoff.kora.application.graph;

    requires transitive org.slf4j;
    requires transitive org.jspecify;
    requires static java.management;
}
