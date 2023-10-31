package ru.tinkoff.kora.test.extension.junit5;

import org.mockito.Mockito;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;

record GraphMockitoSpy(GraphCandidate candidate, Class<?> mockClass, Object value) implements GraphModification {

    public static GraphModification ofField(GraphCandidate candidate, Field field, Object testClassInstance) {
        var classToMock = getClassToMock(candidate);
        try {
            field.setAccessible(true);
            var inst = field.get(testClassInstance);
            if (inst == null) {
                throw new IllegalArgumentException("Can't @Spy component '%s' because it is null, initialize field first".formatted(candidate.toString()));
            }

            return new GraphMockitoSpy(candidate, classToMock, inst);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Can't extract @Spy component '%s' from: %s".formatted(candidate.type(), testClassInstance));
        }
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
    private <T> void replaceNode(ApplicationGraphDraw graphDraw, Node<?> node) {
        var casted = (Node<T>) node;
        graphDraw.replaceNode(casted, g -> ((T) Mockito.spy(value)));
    }
}
