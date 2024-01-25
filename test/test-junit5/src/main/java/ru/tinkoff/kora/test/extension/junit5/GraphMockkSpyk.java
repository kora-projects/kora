package ru.tinkoff.kora.test.extension.junit5;

import io.mockk.impl.JvmMockKGateway;
import io.mockk.impl.annotations.SpyK;
import jakarta.annotation.Nullable;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;
import ru.tinkoff.kora.application.graph.internal.NodeImpl;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;

record GraphMockkSpyk(GraphCandidate candidate,
                      Class<?> mockClass,
                      @Nullable Object value,
                      String spykName,
                      boolean recordPrivateCalls) implements GraphModification {

    static GraphModification ofAnnotated(GraphCandidate candidate, AnnotatedElement element, String defaultName) {
        var classToMock = getClassToMock(candidate);
        var annotation = MockUtils.getAnnotation(element, SpyK.class);
        var name = Optional.of(annotation.name())
                .filter(n -> !n.isBlank())
                .orElse(defaultName);
        return new GraphMockkSpyk(candidate, classToMock, null, name, annotation.recordPrivateCalls());
    }

    static GraphModification ofField(GraphCandidate candidate, Field field, Object fieldValue) {
        var classToMock = getClassToMock(candidate);
        var annotation = MockUtils.getAnnotation(field, SpyK.class);
        var name = Optional.of(annotation.name())
            .filter(n -> !n.isBlank())
            .orElseGet(field::getName);
        return new GraphMockkSpyk(candidate, classToMock, fieldValue, name, annotation.recordPrivateCalls());
    }

    public boolean isSpyGraph() {
        return value == null;
    }

    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var nodesToMock = GraphUtils.findNodeByTypeOrAssignable(graphDraw, candidate());
        if (nodesToMock.isEmpty()) {
            throw new IllegalArgumentException("Can't @SpyK component '%s' because it is not present in graph".formatted(candidate.toString()));
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
        throw new IllegalArgumentException("Can't @SpyK using MockK for type: " + candidate);
    }

    @SuppressWarnings("unchecked")
    private <T> void replaceNode(ApplicationGraphDraw graphDraw, Node<?> node) {
        var casted = (Node<T>) node;

        if (value != null) {
            graphDraw.replaceNode(casted, g -> {
                var spyCandidate = (T) value;
                return getSpy(spyCandidate, spykName, recordPrivateCalls);
            });
        } else {
            graphDraw.replaceNodeKeepDependencies(casted, g -> {
                var spyCandidate = ((NodeImpl<T>) node).factory.get(g);
                return getSpy(spyCandidate, spykName, recordPrivateCalls);
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getSpy(T spyCandidate, String spykName, boolean recordPrivateCalls) {
        final KClass<T> kotlinClass = JvmClassMappingKt.getKotlinClass(((Class<T>) spyCandidate.getClass()));
        return JvmMockKGateway.Companion.getDefaultImplementation().getMockFactory().spyk(kotlinClass, spyCandidate, spykName, new KClass[]{}, recordPrivateCalls);
    }
}
