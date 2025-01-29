package ru.tinkoff.kora.test.extension.junit5;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.quality.Strictness;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;
import ru.tinkoff.kora.application.graph.Wrapped;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;

record GraphMockitoMock(GraphCandidate candidate,
                        Class<?> mockClass,
                        String name,
                        Mock annotation) implements GraphModification {

    public static GraphModification ofAnnotated(GraphCandidate candidate, AnnotatedElement element, String defaultName) {
        var annotation = element.getAnnotation(Mock.class);
        var name = Optional.of(annotation.name())
            .filter(n -> !n.isBlank())
            .orElse(defaultName);

        return new GraphMockitoMock(candidate, getClassToMock(candidate), name, annotation);
    }

    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var nodesToMock = GraphUtils.findNodeByTypeOrAssignable(graphDraw, candidate());
        if (nodesToMock.isEmpty()) {
            throw new IllegalArgumentException("Can't @Mock component '%s' because it is not present in graph".formatted(candidate.toString()));
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
        throw new IllegalArgumentException("Can't @Mock using Mockito for type: " + candidate);
    }

    @SuppressWarnings("unchecked")
    private <T> void replaceNode(ApplicationGraphDraw graphDraw, Node<T> node, Class<?> mockClass) {
        graphDraw.replaceNode(node, g -> {
            var settings = new MockSettingsImpl<>()
                .name(name)
                .defaultAnswer(annotation.answer());

            if (!annotation.mockMaker().isBlank()) {
                settings = settings.mockMaker(annotation.mockMaker());
            }

            if (annotation.extraInterfaces().length != 0) {
                settings = settings.extraInterfaces(annotation.extraInterfaces());
            }

            if (annotation.strictness() != Mock.Strictness.TEST_LEVEL_DEFAULT) {
                var strictLevel = switch (annotation.strictness()) {
                    case WARN -> Strictness.WARN;
                    case LENIENT -> Strictness.LENIENT;
                    case STRICT_STUBS -> Strictness.STRICT_STUBS;
                    default -> throw new UnsupportedOperationException("Unknown strictness level provided: " + annotation.strictness());
                };

                settings = settings.strictness(strictLevel);
            }

            if (annotation.withoutAnnotations()) {
                settings = settings.withoutAnnotations();
            }
            if (annotation.stubOnly()) {
                settings = settings.stubOnly();
            }
            if (annotation.serializable()) {
                settings = settings.serializable();
            }

            var mock = (T) Mockito.mock(mockClass, settings);
            if (node.type() instanceof Class<?> tc && Wrapped.class.isAssignableFrom(tc)) {
                return (T) (Wrapped<?>) () -> mock;
            } else if (node.type() instanceof ParameterizedType pt && Wrapped.class.isAssignableFrom(((Class<?>) pt.getRawType()))) {
                return (T)(Wrapped<?>) () -> mock;
            } else {
                return mock;
            }
        });
    }
}
