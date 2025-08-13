package ru.tinkoff.kora.test.extension.junit5;

import io.mockk.MockKKt;
import io.mockk.impl.annotations.MockK;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;
import ru.tinkoff.kora.application.graph.Wrapped;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;

record GraphMockkMock(GraphCandidate candidate,
                      Class<?> mockClass,
                      String mockkName,
                      boolean relaxed,
                      boolean relaxUnitFun) implements GraphModification {

    public static GraphModification ofAnnotated(GraphCandidate candidate, AnnotatedElement element, String defaultName) {
        var annotation = MockUtils.getAnnotation(element, MockK.class);

        if (annotation == null) {
            throw new IllegalArgumentException("Can't @MockK %s because it is not annotated with @MockK");
        }

        var classToMock = getClassToMock(candidate);
        var name = Optional.of(annotation.name())
            .filter(n -> !n.isBlank())
            .orElse(defaultName);

        return new GraphMockkMock(candidate, classToMock, name, annotation.relaxed(), annotation.relaxUnitFun());
    }

    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var nodesToMock = GraphUtils.findNodeByTypeOrAssignable(graphDraw, candidate());
        if (nodesToMock.isEmpty()) {
            throw new IllegalArgumentException("Can't @MockK component %s because it is not present in graph".formatted(candidate.toString()));
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
        throw new IllegalArgumentException("Can't @MockK using MockK for type: " + candidate);
    }

    @SuppressWarnings("unchecked")
    private <T> void replaceNode(ApplicationGraphDraw graphDraw, Node<T> node, Class<?> mockClass) {
        graphDraw.replaceNode(node, g -> {
            var kotlinClass = JvmClassMappingKt.getKotlinClass(mockClass);

            boolean isNodeWrapped = node.type() instanceof Class<?> tc && Wrapped.class.isAssignableFrom(tc);
            var mock = (T) MockKKt.mockkClass(kotlinClass, null, isNodeWrapped || relaxed, new KClass<?>[]{}, isNodeWrapped || relaxUnitFun, v -> null);

            Optional<Class<?>> wrappedType = GraphUtils.findWrappedType(node.type());
            if (wrappedType.isPresent() && wrappedType.get().isInstance(mock)) {
                Optional<Class<?>> nodeClass = GraphUtils.tryCastType(node.type());
                if (nodeClass.isPresent()) {
                    if (nodeClass.get().equals(Wrapped.class)) {
                        return (T) (Wrapped<T>) () -> mock;
                    } else {
                        var kotlinTC = JvmClassMappingKt.getKotlinClass(nodeClass.get());
                        Wrapped<T> mockedWrapper = (Wrapped<T>) MockKKt.mockkClass(kotlinTC, null, true, new KClass<?>[]{}, true, v -> null);
                        MockKKt.every(mockKMatcherScope -> mockedWrapper.value()).returns(mock);
                        return (T) mockedWrapper;
                    }
                }
            }

            return mock;
        });
    }
}
