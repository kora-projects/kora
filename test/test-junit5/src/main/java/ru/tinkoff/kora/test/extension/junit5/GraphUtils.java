package ru.tinkoff.kora.test.extension.junit5;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.Tag;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

final class GraphUtils {

    private GraphUtils() {}

    static <T> Set<Node<T>> findNodeByType(ApplicationGraphDraw graph, GraphCandidate candidate) {
        return findNodeByType(graph, candidate.type(), candidate.tag());
    }

    @SuppressWarnings("unchecked")
    static <T> Set<Node<T>> findNodeByType(ApplicationGraphDraw graph, Type type, @Nullable Class<?> tag) {
        if (tag == null || Objects.equals(Tag.Any.class, tag)) {
            final Set<Node<T>> nodes = new HashSet<>();
            for (var graphNode : graph.getNodes()) {
                if (graphNode.tag() == null) {
                    if (graphNode.type().equals(type)) {
                        nodes.add((Node<T>) graphNode);
                    } else {
                        final Type unwrappedType = unwrap(graphNode.type());
                        if (unwrappedType.equals(type)) {
                            nodes.add((Node<T>) graphNode);
                        }
                    }
                }
            }

            return nodes;
        } else {
            for (var graphNode : graph.getNodes()) {
                if (Objects.equals(tag, graphNode.tag())) {
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
        return findNodeByTypeOrAssignable(graph, candidate.type(), candidate.tag());
    }

    static Set<Node<?>> findNodeByTypeOrAssignable(ApplicationGraphDraw graph, Type type, Class<?> tag) {
        if (tag == null || Objects.equals(Tag.Any.class, tag)) {
            final Set<Node<?>> nodes = new HashSet<>();
            for (var graphNode : graph.getNodes()) {
                if (graphNode.tag() == null || Objects.equals(Tag.Any.class, graphNode.tag())) {
                    Type graphType = graphNode.type();
                    if (isTypeAssignable(graphType, type)) {
                        nodes.add(graphNode);
                    } else {
                        final Type unwrappedType = unwrap(graphType);
                        if (graphType != unwrappedType && isTypeAssignable(unwrappedType, type)) {
                            nodes.add(graphNode);
                        }
                    }
                }
            }

            return nodes;
        } else {
            for (var graphNode : graph.getNodes()) {
                if (Objects.equals(tag, graphNode.tag())) {
                    Type graphType = graphNode.type();
                    if (isTypeAssignable(graphType, type)) {
                        return Set.of(graphNode);
                    } else {
                        final Type unwrappedType = unwrap(graphType);
                        if (graphType != unwrappedType && isTypeAssignable(unwrappedType, type)) {
                            return Set.of(graphNode);
                        }
                    }
                }
            }
        }

        return Set.of();
    }

    static boolean isTypeAssignable(Type type, Type candidate) {
        if (type.equals(candidate)) {
            return true;
        }

        if (type instanceof Class<?> tt && candidate instanceof ParameterizedType cpt) {
            Type[] genericInterfaces = tt.getGenericInterfaces();
            for (Type genericInterface : genericInterfaces) {
                if (isTypeAssignable(genericInterface, cpt)) {
                    return true;
                }
            }
        } else if (type instanceof ParameterizedType tpt && candidate instanceof Class<?> ct) {
            Type[] genericInterfaces = ct.getGenericInterfaces();
            for (Type genericInterface : genericInterfaces) {
                if (isTypeAssignable(tpt, genericInterface)) {
                    return true;
                }
            }
        }

        List<Class<?>> typeFlat = getTypeFlat(type);
        List<Class<?>> candidateFlat = getTypeFlat(candidate);

        if (typeFlat.size() == candidateFlat.size()) {
            for (int i = 0; i < typeFlat.size(); i++) {
                Class<?> iType = typeFlat.get(i);
                Class<?> iCandidate = candidateFlat.get(i);
                if (!iCandidate.isAssignableFrom(iType)) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    static List<Class<?>> getTypeFlat(Type type) {
        if (type instanceof Class<?> tc) {
            return List.of(tc);
        } else if (type instanceof ParameterizedType ptt) {
            List<Class<?>> rawFlat = getTypeFlat(ptt.getRawType());
            List<Class<?>> argsFlat = Arrays.stream(ptt.getActualTypeArguments())
                .flatMap(t -> getTypeFlat(t).stream())
                .toList();

            var result = new ArrayList<Class<?>>();
            result.addAll(rawFlat);
            result.addAll(argsFlat);
            return result;
        } else if (type instanceof GenericArrayType ptt) {
            return getTypeFlat(ptt.getGenericComponentType());
        } else {
            return List.of();
        }
    }

    static boolean isWrapped(Type type) {
        Type unwrapped = unwrap(type);
        return type != unwrapped;
    }

    static boolean doesImplement(Class<?> aClass, ParameterizedType parameterizedType) {
        for (var genericInterface : aClass.getGenericInterfaces()) {
            if (genericInterface.equals(parameterizedType)) {
                return true;
            }
        }
        return false;
    }

    static boolean doesImplementOrExtend(Class<?> aClass, ParameterizedType parameterizedType) {
        if (doesImplement(aClass, parameterizedType)) {
            return true;
        }
        var superclass = aClass.getGenericSuperclass();
        if (superclass == null) {
            return false;
        }
        if (superclass.equals(parameterizedType)) {
            return true;
        }
        if (superclass instanceof Class<?> clazz) {
            return doesImplementOrExtend(clazz, parameterizedType);
        }
        if (superclass instanceof ParameterizedType clazz) {
            return doesImplementOrExtend((Class<?>) clazz.getRawType(), parameterizedType);
        }
        return false;
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
        } else if (type instanceof ParameterizedType pt
            && pt.getRawType() instanceof Class<?> pct
            && Wrapped.class.isAssignableFrom(pct)) {
            return pt.getActualTypeArguments()[0];
        } else {
            return type;
        }
    }

    static Optional<Class<?>> findWrappedType(Type nodeType) {
        if (nodeType instanceof Class<?> tc && Wrapped.class.isAssignableFrom(tc)) {
            return Arrays.stream(tc.getGenericInterfaces())
                .filter(i -> i instanceof ParameterizedType pt
                    && pt.getRawType() instanceof Class<?> ptc
                    && ptc.equals(Wrapped.class))
                .map(i -> ((ParameterizedType) i).getActualTypeArguments()[0])
                .<Class<?>>map(t -> tryCastType(t).orElse(null))
                .filter(Objects::nonNull)
                .findFirst();
        } else if (nodeType instanceof ParameterizedType pt
            && pt.getRawType() instanceof Class<?> ptc
            && Wrapped.class.isAssignableFrom(ptc)) {
            if (Wrapped.class.equals(ptc)) {
                return tryCastType(pt.getActualTypeArguments()[0]);
            } else {
                return Arrays.stream(ptc.getGenericInterfaces())
                    .filter(i -> i instanceof ParameterizedType ppt
                        && ppt.getRawType() instanceof Class<?> pptc
                        && pptc.equals(Wrapped.class))
                    .map(i -> ((ParameterizedType) i).getActualTypeArguments()[0])
                    .<Class<?>>map(t -> tryCastType(t).orElse(null))
                    .filter(Objects::nonNull)
                    .findFirst();
            }
        }

        return Optional.empty();
    }

    static Optional<Class<?>> tryCastType(Type type) {
        if (type instanceof Class<?> tc) {
            return Optional.of(tc);
        } else if (type instanceof ParameterizedType tp) {
            return (tp.getRawType() instanceof Class<?> ptc)
                ? Optional.of(ptc)
                : tryCastType(tp.getRawType());
        } else if (type instanceof GenericArrayType gat) {
            return tryCastType(gat.getGenericComponentType());
        } else {
            return Optional.empty();
        }
    }
}
