package ru.tinkoff.kora.test.extension.junit5;

import jakarta.annotation.Nullable;
import org.mockito.Mockito;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.application.graph.internal.NodeImpl;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;

record GraphMockitoSpy(GraphCandidate candidate,
                       Class<?> mockClass,
                       @Nullable Object value) implements GraphModification {

    public static GraphModification ofAnnotated(GraphCandidate candidate, AnnotatedElement element) {
        var classToMock = getClassToMock(candidate);
        return new GraphMockitoSpy(candidate, classToMock, null);
    }

    public static GraphModification ofField(GraphCandidate candidate, Field field, Object fieldValue) {
        var classToMock = getClassToMock(candidate);
        return new GraphMockitoSpy(candidate, classToMock, fieldValue);
    }

    public boolean isSpyGraph() {
        return value == null;
    }

    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var nodesToMock = GraphUtils.findNodeByTypeOrAssignable(graphDraw, candidate());
        if (nodesToMock.isEmpty()) {
            throw new IllegalArgumentException("Can't @Spy component '%s' because it is not present in graph".formatted(candidate.toString()));
        }

        for (var nodeToMock : nodesToMock) {
            replaceNode(graphDraw, nodeToMock);
        }
    }

    private static Class<?> getClassToMock(GraphCandidate candidate) {
        if (candidate.type() instanceof Class<?> clazz) {
            return clazz;
        }
        if (candidate.type() instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        throw new IllegalArgumentException("Can't @Spy using Mockito for type: " + candidate);
    }

    @SuppressWarnings("unchecked")
    private void replaceNode(ApplicationGraphDraw graphDraw, Node node) {
        if (value != null) {
            graphDraw.replaceNode(node, g -> {
                var spyCandidate = value;
                return getSpy(spyCandidate, node);
            });
        } else {
            graphDraw.replaceNodeKeepDependencies(node, g -> {
                var spyCandidate = ((NodeImpl<?>) node).factory.get(g);
                return getSpy(spyCandidate, node);
            });
        }
    }

    private Object getSpy(Object spyCandidate, Node node) {
        var spy = Mockito.spy(spyCandidate);
        if (node.type() instanceof Class<?> tc && Wrapped.class.isAssignableFrom(tc)) {
            return (Object) (Wrapped<?>) () -> spy;
        } else if (node.type() instanceof ParameterizedType pt && Wrapped.class.isAssignableFrom(((Class<?>) pt.getRawType()))) {
            return (Object) (Wrapped<?>) () -> spy;
        } else {
            return spy;
        }
    }
}
