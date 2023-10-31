package ru.tinkoff.kora.test.extension.junit5;

import io.mockk.MockKKt;
import io.mockk.impl.annotations.MockK;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;

record GraphMockkMock(GraphCandidate candidate, Class<?> mockClass, String mockkName, boolean relaxed, boolean relaxUnitFun) implements GraphModification {

    public static GraphModification ofAnnotated(GraphCandidate candidate, AnnotatedElement element, String defaultName) {
        var annotation = element.getAnnotation(MockK.class);
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
    private <T> void replaceNode(ApplicationGraphDraw graphDraw, Node<?> node, Class<T> mockClass) {
        var casted = (Node<T>) node;
        graphDraw.replaceNode(casted, g -> {
            KClass<T> kotlinClass = JvmClassMappingKt.getKotlinClass(mockClass);
            return MockKKt.mockkClass(kotlinClass, null, relaxed, new KClass[]{}, relaxUnitFun, v -> null);
        });
    }
}
