@SuppressWarnings("module")
module kora.application.graph {
    exports ru.tinkoff.kora.application.graph;
    exports ru.tinkoff.kora.application.graph.internal.loom to kora.common;

    requires transitive org.slf4j;
    requires transitive static jakarta.annotation;
    requires static java.management;
    requires static io.goodforgod.graalvm.hint.annotations;
}
