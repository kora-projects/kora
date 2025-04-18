package ru.tinkoff.kora.test.extension.junit5;

import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.RefreshableGraph;

record TestGraphContext(RefreshableGraph refreshableGraph,
                        ApplicationGraphDraw graphDraw,
                        KoraAppGraph koraAppGraph) {
}
