package io.koraframework.test.extension.junit5;

import io.koraframework.application.graph.ApplicationGraphDraw;

import java.util.function.Function;

record GraphAddition(Function<KoraAppGraph, ?> function, GraphCandidate candidate) implements GraphModification {

    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var tags = candidate().tag();

        graphDraw.addNode(candidate().type(), tags, null, null, null, null, null, g -> function.apply(new TestKoraAppGraph(graphDraw, g)));
    }
}
