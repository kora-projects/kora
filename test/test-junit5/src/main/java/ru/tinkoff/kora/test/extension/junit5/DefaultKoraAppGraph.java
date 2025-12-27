package ru.tinkoff.kora.test.extension.junit5;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Graph;
import ru.tinkoff.kora.common.Tag;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unchecked")
final class DefaultKoraAppGraph implements KoraAppGraph {

    private final ApplicationGraphDraw graphDraw;
    private final Graph graph;

    DefaultKoraAppGraph(ApplicationGraphDraw graphDraw, Graph graph) {
        this.graphDraw = graphDraw;
        this.graph = graph;
    }

    @Nullable
    @Override
    public Object getFirst(Type type) {
        var node = graphDraw.findNodeByType(type);
        return (node == null)
            ? null
            : graph.get(node);
    }

    @Nullable
    @Override
    public <T> T getFirst(Class<T> type) {
        return type.cast(getFirst(((Type) type)));
    }

    @Nullable
    @Override
    public Object getFirst(Type type, @Nullable Class<?> tag) {
        var nodes = GraphUtils.findNodeByType(graphDraw, new GraphCandidate(type, tag));
        return nodes.stream()
            .map(graph::get)
            .findFirst()
            .orElse(null);
    }

    @Nullable
    @Override
    public <T> T getFirst(Class<T> type, @Nullable Class<?> tag) {
        return (T) getFirst((Type) type, tag);
    }

    @Override
    public List<Object> getAll(Type type) {
        return getAll(type, Tag.Any.class);
    }

    @Override
    public List<Object> getAll(Type type, @Nullable Class<?> tag) {
        var nodes = GraphUtils.findNodeByType(graphDraw, new GraphCandidate(type, tag));
        return nodes.stream()
            .map(graph::get)
            .toList();
    }

    @Override
    public <T> List<T> getAll(Class<T> type) {
        return getAll(type, Tag.Any.class);
    }

    @Override
    public <T> List<T> getAll(Class<T> type, @Nullable Class<?> tag) {
        return (List<T>) getAll(((Type) type), tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultKoraAppGraph that)) return false;
        return Objects.equals(graph, that.graph);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graph);
    }
}
