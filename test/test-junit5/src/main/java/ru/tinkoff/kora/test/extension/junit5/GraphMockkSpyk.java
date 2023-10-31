package ru.tinkoff.kora.test.extension.junit5;

import io.mockk.impl.JvmMockKGateway;
import io.mockk.impl.annotations.SpyK;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;

record GraphMockkSpyk(GraphCandidate candidate, Class<?> mockClass, Object value, String spykName,
                      boolean recordPrivateCalls) implements GraphModification {

    static GraphModification ofField(GraphCandidate candidate, Field field, Object testClassInstance) {
        var classToMock = getClassToMock(candidate);
        try {
            field.setAccessible(true);
            var inst = field.get(testClassInstance);
            if (inst == null) {
                throw new IllegalArgumentException("Can't @SpyK component '%s' because it is null, initialize field first".formatted(candidate.toString()));
            }

            var annotation = field.getAnnotation(SpyK.class);
            var name = Optional.of(annotation.name())
                    .filter(n -> !n.isBlank())
                    .orElseGet(field::getName);
            return new GraphMockkSpyk(candidate, classToMock, inst, name, annotation.recordPrivateCalls());
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Can't extract @SpyK component '%s' from: %s".formatted(candidate.type(), testClassInstance));
        }
    }

    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var nodesToMock = GraphUtils.findNodeByTypeOrAssignable(graphDraw, candidate());
        if (nodesToMock.isEmpty()) {
            throw new IllegalArgumentException("Can't @SpyK component '%s' because it is not present in graph".formatted(candidate.toString()));
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
        throw new IllegalArgumentException("Can't @SpyK using MockK for type: " + candidate);
    }

    @SuppressWarnings("unchecked")
    private <T> void replaceNode(ApplicationGraphDraw graphDraw, Node<?> node, Class<T> mockClass) {
        var casted = (Node<T>) node;
        graphDraw.replaceNode(casted, g -> {
            KClass<T> kotlinClass = JvmClassMappingKt.getKotlinClass(mockClass);
            return JvmMockKGateway.Companion.getDefaultImplementation().getMockFactory().spyk(kotlinClass, ((T) value), spykName, new KClass[]{}, true);
        });
    }
}
