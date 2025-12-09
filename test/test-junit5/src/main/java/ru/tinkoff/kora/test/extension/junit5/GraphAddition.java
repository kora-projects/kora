package ru.tinkoff.kora.test.extension.junit5;

import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;

import java.util.function.Function;

record GraphAddition(Function<KoraAppGraph, ?> function, GraphCandidate candidate) implements GraphModification {

    @Override
    public void accept(ApplicationGraphDraw graphDraw) {
        var tags = candidate().tag();

        graphDraw.addNode0(candidate().type(), tags, g -> function.apply(new DefaultKoraAppGraph(graphDraw, g)));
    }
}
