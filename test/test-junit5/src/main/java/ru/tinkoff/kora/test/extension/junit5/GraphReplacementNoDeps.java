package ru.tinkoff.kora.test.extension.junit5;

import io.mockk.MockKKt;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.mockito.Mockito;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;
import ru.tinkoff.kora.application.graph.Wrapped;

import java.util.Optional;
import java.util.function.Function;

record GraphReplacementNoDeps<T>(Function<KoraAppGraph, ? extends T> function,
                                 GraphCandidate candidate) implements GraphModification {
    GraphReplacementNoDeps {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var nodesToReplace = GraphUtils.findNodeByTypeOrAssignable(graphDraw, candidate());
        if (nodesToReplace.isEmpty()) {
            throw new ExtensionConfigurationException("Can't find Nodes to Replace: " + candidate());
        }

        for (var node : nodesToReplace) {
            var casted = (Node<Object>) node;
            graphDraw.replaceNode(casted, g -> {
                var replacement = function.apply(new DefaultKoraAppGraph(graphDraw, g));

                Optional<Class<?>> wrappedType = GraphUtils.findWrappedType(node.type());
                if (wrappedType.isPresent() && wrappedType.get().isInstance(replacement)) {
                    Optional<Class<?>> mockClass = GraphUtils.tryCastType(node.type());
                    if (mockClass.isPresent() && !mockClass.get().equals(Wrapped.class) && MockUtils.haveAnyMockEngine()) {
                        if (MockUtils.isMockitoAvailable()) {
                            Wrapped<T> mockedWrapper = (Wrapped<T>) Mockito.mock(mockClass.get());
                            Mockito.when(mockedWrapper.value()).thenReturn(replacement);
                            return mockedWrapper;
                        } else {
                            var kotlinTC = JvmClassMappingKt.getKotlinClass(mockClass.get());
                            Wrapped<T> mockedWrapper = (Wrapped<T>) MockKKt.mockkClass(kotlinTC, null, true, new KClass<?>[]{}, true, v -> null);
                            MockKKt.every(mockKMatcherScope -> mockedWrapper.value()).returns(replacement);
                            return mockedWrapper;
                        }
                    }

                    return (Wrapped<T>) () -> replacement;
                } else {
                    return replacement;
                }
            });
        }
    }
}
