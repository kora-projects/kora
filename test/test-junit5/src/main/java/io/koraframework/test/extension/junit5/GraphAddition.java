package io.koraframework.test.extension.junit5;

import io.koraframework.application.graph.ApplicationGraphDraw;

import java.util.List;
import java.util.function.Function;

record GraphAddition(Function<KoraAppGraph, ?> function, GraphCandidate candidate) implements GraphModification {

    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var tags = candidate().tag();

        graphDraw.addNode(candidate().type(), tags, List.of(), List.of(), List.of(), g -> function.apply(new DefaultKoraAppGraph(graphDraw, g)));
    }
}
