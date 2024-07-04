package ru.tinkoff.kora.test.extension.junit5;

import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;
import ru.tinkoff.kora.application.graph.internal.NodeImpl;

import java.util.function.BiFunction;

record GraphProxy<T>(BiFunction<T, KoraAppGraph, ? extends T> function,
                     GraphCandidate candidate) implements GraphModification {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var nodesToReplace = GraphUtils.findNodeByTypeOrAssignable(graphDraw, candidate());
        if (nodesToReplace.isEmpty()) {
            throw new ExtensionConfigurationException("Can't find Nodes to Proxy: " + candidate());
        }

        for (var nodeToReplace : nodesToReplace) {
            var casted = (Node<T>) nodeToReplace;
            graphDraw.replaceNodeKeepDependencies(casted, g -> {
                var self = ((NodeImpl) casted).factory.get(g);
                return function.apply((T) self, new DefaultKoraAppGraph(graphDraw, g));
            });
        }
    }
}
