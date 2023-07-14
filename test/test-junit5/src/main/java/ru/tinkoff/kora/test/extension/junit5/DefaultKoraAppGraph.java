package ru.tinkoff.kora.test.extension.junit5;

import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Graph;
import ru.tinkoff.kora.common.Tag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unchecked")
final class DefaultKoraAppGraph implements KoraAppGraph {

    private static final Class<?>[] TAG_ANY = new Class[]{Tag.Any.class};

    private final ApplicationGraphDraw graphDraw;
    private final Graph graph;

    DefaultKoraAppGraph(ApplicationGraphDraw graphDraw, Graph graph) {
        this.graphDraw = graphDraw;
        this.graph = graph;
    }

    @Nullable
    @Override
    public Object getFirst(@Nonnull Type type) {
        var node = graphDraw.findNodeByType(type);
        return (node == null)
            ? null
            : graph.get(node);
    }

    @Nullable
    @Override
    public <T> T getFirst(@Nonnull Class<T> type) {
        return (T) getFirst(((Type) type));
    }

    @Nullable
    @Override
    public Object getFirst(@Nonnull Type type, Class<?>... tags) {
        var nodes = GraphUtils.findNodeByType(graphDraw, new GraphCandidate(type, tags));
        return nodes.stream()
            .map(graph::get)
            .findFirst()
            .orElse(null);
    }

    @Nullable
    @Override
    public <T> T getFirst(@Nonnull Class<T> type, Class<?>... tags) {
        return (T) getFirst((Type) type, tags);
    }

    @Nonnull
    @Override
    public List<Object> getAll(@Nonnull Type type) {
        return getAll(type, TAG_ANY);
    }

    @Nonnull
    @Override
    public List<Object> getAll(@Nonnull Type type, Class<?>... tags) {
        var nodes = GraphUtils.findNodeByType(graphDraw, new GraphCandidate(type, tags));
        return nodes.stream()
            .map(graph::get)
            .toList();
    }

    @Nonnull
    @Override
    public <T> List<T> getAll(@Nonnull Class<T> type) {
        return getAll(type, TAG_ANY);
    }

    @Nonnull
    @Override
    public <T> List<T> getAll(@Nonnull Class<T> type, Class<?>... tags) {
        return (List<T>) getAll(((Type) type), tags);
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
