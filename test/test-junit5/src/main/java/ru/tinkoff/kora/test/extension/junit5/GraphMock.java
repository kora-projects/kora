package ru.tinkoff.kora.test.extension.junit5;

import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Node;

import java.lang.reflect.ParameterizedType;

record GraphMock(GraphCandidate candidate, Class<?> mockClass) implements GraphModification {
    GraphMock(GraphCandidate candidate) {
        this(candidate, getClassToMock(candidate));
    }

    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var nodesToMock = GraphUtils.findNodeByTypeOrAssignable(graphDraw, candidate());
        if (nodesToMock.isEmpty()) {
            throw new IllegalArgumentException("Can't mock component %s because it is not present in graph".formatted(candidate.toString()));
        }
        for (var nodeToMock : nodesToMock) {
            replaceNode(graphDraw, nodeToMock, mockClass());
        }
    }

    private static Class<?> getClassToMock(GraphCandidate candidate) {
        if (candidate.type() instanceof Class<?> clazz) {
            return clazz;
        }
        if (candidate.type() instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        throw new IllegalArgumentException("Can't mock type: " + candidate);
    }

    private static <T> void replaceNode(ApplicationGraphDraw graphDraw, Node<?> node, Class<T> mockClass) {
        @SuppressWarnings("unchecked")
        var casted = (Node<T>) node;
        graphDraw.replaceNode(casted, g -> {
            var mock = Mockito.mock(mockClass);
            if (Lifecycle.class.isAssignableFrom(mockClass)) {
                Mockito.when(((Lifecycle) mock).init()).thenReturn(Mono.empty());
                Mockito.when(((Lifecycle) mock).release()).thenReturn(Mono.empty());
            }
            return mock;
        });
    }
}
