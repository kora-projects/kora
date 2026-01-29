package ru.tinkoff.kora.test.extension.junit5;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Graph;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.Tag;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unchecked")
final class DefaultKoraAppGraph implements KoraAppGraph {

    private static final Class<?>[] TAG_ANY = new Class<?>[]{Tag.Any.class};

    private final ApplicationGraphDraw graphDraw;
    private final Graph graph;

    DefaultKoraAppGraph(ApplicationGraphDraw graphDraw, Graph graph) {
        this.graphDraw = graphDraw;
        this.graph = graph;
    }

    @Nullable
    @Override
    public Object getFirst(@Nonnull Type type) {
        return getFirst(type, null);
    }

    @Nullable
    @Override
    public <T> T getFirst(@Nonnull Class<T> type) {
        var value = getFirst(((Type) type));
        return type.cast(value);
    }

    @Nullable
    @Override
    public Object getFirst(@Nonnull Type type, Class<?>... tags) {
        if (tags == null) {
            var node = graphDraw.findNodeByType(type);
            if (node != null) {
                var value = graph.get(node);
                return unwrap(type, value);
            }
        } else {
            var nodesByType = graphDraw.findNodesByType(type, tags);
            if (!nodesByType.isEmpty()) {
                var value = graph.get(nodesByType.iterator().next());
                return unwrap(type, value);
            }
        }

        var nodes = GraphUtils.findNodeByTypeOrAssignable(graphDraw, type, tags);
        if(nodes.isEmpty()) {
            return null;
        } else {
            var value = graph.get(nodes.iterator().next());
            return unwrap(type, value);
        }
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
        var nodesByType = graphDraw.findNodesByType(type, tags);
        if (!nodesByType.isEmpty()) {
            return nodesByType.stream()
                .map(n -> {
                    var value = graph.get(n);
                    return unwrap(type, value);
                })
                .toList();
        }

        var nodes = GraphUtils.findNodeByTypeOrAssignable(graphDraw, type, tags);
        return nodes.stream()
            .map(n -> {
                var value = graph.get(n);
                return unwrap(type, value);
            })
            .toList();
    }

    private static Object unwrap(Type type, Object value) {
        if (value instanceof Wrapped<?> wrapped && !GraphUtils.isTypeAssignable(type, Wrapped.class)) {
            return wrapped.value();
        } else if (value instanceof ValueOf<?> valOf && !GraphUtils.isTypeAssignable(type, ValueOf.class)) {
            return valOf.get();
        } else {
            return value;
        }
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
