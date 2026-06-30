import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.application.graph {
    exports io.koraframework.application.graph;

    requires transitive org.slf4j;
    requires transitive org.jspecify;
    requires static java.management;
    requires jdk.unsupported;
}
