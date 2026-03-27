package io.koraframework.test.extension.junit5;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.InitializedGraph;

record TestGraphContext(InitializedGraph initializedGraph,
                        ApplicationGraphDraw graphDraw,
                        KoraAppGraph koraAppGraph) {
}
