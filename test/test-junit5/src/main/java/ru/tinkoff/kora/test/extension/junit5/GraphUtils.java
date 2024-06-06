package ru.tinkoff.kora.test.extension.junit5;

import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.Tag;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

final class GraphUtils {

    private static final Class<?>[] TAG_ANY = new Class<?>[]{Tag.Any.class};

    private GraphUtils() {}

    static <T> Set<Node<T>> findNodeByType(ApplicationGraphDraw graph, GraphCandidate candidate) {
        return findNodeByType(graph, candidate.type(), candidate.tagsAsArray());
    }

    @SuppressWarnings("unchecked")
    static <T> Set<Node<T>> findNodeByType(ApplicationGraphDraw graph, Type type, Class<?>[] tags) {
        if (tags == null || tags.length == 0) {
            final Node<T> node = (Node<T>) graph.findNodeByType(type);
            return (node == null)
                ? Set.of()
                : Set.of(node);
        } else if (Arrays.equals(TAG_ANY, tags)) {
            final Set<Node<T>> nodes = new HashSet<>();
            for (var graphNode : graph.getNodes()) {
                if (graphNode.type().equals(type)) {
                    nodes.add((Node<T>) graphNode);
                } else {
                    final Type unwrappedType = unwrap(graphNode.type());
                    if (unwrappedType.equals(type)) {
                        nodes.add((Node<T>) graphNode);
                    }
                }
            }
            return nodes;
        } else {
            for (var graphNode : graph.getNodes()) {
                if (Arrays.equals(tags, graphNode.tags())) {
                    if (graphNode.type().equals(type)) {
                        return Set.of((Node<T>) graphNode);
                    } else {
                        final Type unwrappedType = unwrap(graphNode.type());
                        if (unwrappedType.equals(type)) {
                            return Set.of((Node<T>) graphNode);
                        }
                    }
                }
            }
        }

        return Set.of();
    }

    static Set<Node<?>> findNodeByTypeOrAssignable(ApplicationGraphDraw graph, GraphCandidate candidate) {
        return findNodeByTypeOrAssignable(graph, candidate.type(), candidate.tagsAsArray());
    }

    static Set<Node<?>> findNodeByTypeOrAssignable(ApplicationGraphDraw graph, Type type, Class<?>[] tags) {
        if (tags == null || tags.length == 0) {
            final Set<Node<?>> nodes = new HashSet<>();
            for (var graphNode : graph.getNodes()) {
                Type graphType = graphNode.type();
                if (graphType.equals(type)) {
                    nodes.add(graphNode);
                } else {
                    final Type unwrappedType = unwrap(graphType);
                    if (unwrappedType.equals(type)) {
                        nodes.add(graphNode);
                        graphType = unwrappedType;
                    }
                }

                var typeClass = tryCastType(type);
                var graphClass = tryCastType(graphType);
                if (typeClass.isPresent() && graphClass.isPresent() && typeClass.get().isAssignableFrom(graphClass.get())) {
                    nodes.add(graphNode);
                }
            }

            return nodes;
        } else if (Arrays.equals(TAG_ANY, tags)) {
            final Set<Node<?>> nodes = new HashSet<>();
            for (var graphNode : graph.getNodes()) {
                Type graphType = graphNode.type();
                if (graphType.equals(type)) {
                    nodes.add(graphNode);
                } else {
                    final Type unwrappedType = unwrap(graphType);
                    if (unwrappedType.equals(type)) {
                        nodes.add(graphNode);
                        graphType = unwrappedType;
                    }
                }

                var typeClass = tryCastType(type);
                var graphClass = tryCastType(graphType);
                if (typeClass.isPresent() && graphClass.isPresent() && typeClass.get().isAssignableFrom(graphClass.get())) {
                    nodes.add(graphNode);
                }
            }

            return nodes;
        } else {
            for (var graphNode : graph.getNodes()) {
                if (Arrays.equals(tags, graphNode.tags())) {
                    if (graphNode.type().equals(type)) {
                        return Set.of(graphNode);
                    } else {
                        final Type unwrappedType = unwrap(graphNode.type());
                        if (unwrappedType.equals(type)) {
                            return Set.of(graphNode);
                        }
                    }

                    var typeClass = tryCastType(type);
                    var graphClass = tryCastType(graphNode.type());
                    var graphClassUnwrapped = tryCastType(graphNode.type());
                    if (typeClass.isPresent() && graphClass.isPresent() && typeClass.get().isAssignableFrom(graphClass.get())) {
                        return Set.of(graphNode);
                    } else if (typeClass.isPresent() && graphClassUnwrapped.isPresent() && typeClass.get().isAssignableFrom(graphClassUnwrapped.get())) {
                        return Set.of(graphNode);
                    }
                }
            }
        }

        return Set.of();
    }

    static Type unwrap(Type type) {
        if (type instanceof Class<?> tc && Wrapped.class.isAssignableFrom(tc)) {
            return Arrays.stream(tc.getGenericInterfaces())
                .filter(i -> i instanceof ParameterizedType)
                .filter(i -> ((ParameterizedType) i).getRawType() instanceof Class<?> rt && Wrapped.class.isAssignableFrom(rt))
                .findFirst()
                .map(i -> ((ParameterizedType) i).getActualTypeArguments()[0])
                .filter(arg -> arg instanceof Class<?>)
                .orElse(type);
        } else if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> pct && Wrapped.class.isAssignableFrom(pct)) {
            return pt.getActualTypeArguments()[0];
        } else {
            return type;
        }
    }

    static Optional<Class<?>> tryCastType(Type type) {
        try {
            if (type instanceof Class<?> tc) {
                return Optional.of(tc);
            } else if (type instanceof ParameterizedType tp) {
                return (tp.getRawType() instanceof Class<?>)
                    ? Optional.ofNullable(((Class<?>) tp.getRawType()))
                    : Optional.ofNullable(KoraJUnit5Extension.class.getClassLoader().loadClass(tp.getRawType().getTypeName()));
            } else {
                return Optional.empty();
            }
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }
}
