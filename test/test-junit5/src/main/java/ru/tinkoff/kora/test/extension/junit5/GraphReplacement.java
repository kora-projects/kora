package ru.tinkoff.kora.test.extension.junit5;

import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;

import java.util.function.Function;

record GraphReplacement<T>(Function<KoraAppGraph, ? extends T> function, GraphCandidate candidate) implements GraphModification {

    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var nodesToReplace = GraphUtils.findNodeByTypeOrAssignable(graphDraw, candidate());
        if (nodesToReplace.isEmpty()) {
            throw new ExtensionConfigurationException("Can't find Nodes to Replace: " + candidate());
        }

        for (var nodeToReplace : nodesToReplace) {
            @SuppressWarnings("unchecked")
            var casted = (Node<T>) nodeToReplace;
            graphDraw.replaceNode(casted, g -> function.apply(new DefaultKoraAppGraph(graphDraw, g)));
        }
    }
}
