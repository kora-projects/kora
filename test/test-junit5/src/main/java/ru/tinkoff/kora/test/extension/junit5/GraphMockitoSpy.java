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
import java.util.Optional;

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
    private <T> void replaceNode(ApplicationGraphDraw graphDraw, Node<T> node) {
        if (value != null) {
            graphDraw.replaceNode(node, g -> {
                var spyCandidate = (T) value;
                return getSpy(spyCandidate, node);
            });
        } else {
            graphDraw.replaceNodeKeepDependencies(node, g -> {
                var spyCandidate = ((NodeImpl<T>) node).factory.get(g);
                return getSpy(spyCandidate, node);
            });
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getSpy(T spyCandidate, Node<T> node) {
        final T spy = (MockUtil.isSpy(spyCandidate))
            ? spyCandidate
            : Mockito.spy(spyCandidate);

        Optional<Class<?>> wrappedType = GraphUtils.findWrappedType(node.type());
        if (wrappedType.isPresent() && wrappedType.get().isInstance(spy)) {
            Optional<Class<?>> nodeClass = GraphUtils.tryCastType(node.type());
            if (nodeClass.isPresent()) {
                if (nodeClass.get().equals(Wrapped.class)) {
                    return (T) (Wrapped<T>) () -> spy;
                } else {
                    Wrapped<T> mockedWrapper = (Wrapped<T>) Mockito.mock(nodeClass.get());
                    Mockito.when(mockedWrapper.value()).thenReturn(spy);
                    return ((T) mockedWrapper);
                }
            }
        }

        return spy;
    }
}
