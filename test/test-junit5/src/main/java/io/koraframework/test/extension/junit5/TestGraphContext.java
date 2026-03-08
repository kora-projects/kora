package io.koraframework.test.extension.junit5;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.RefreshableGraph;

record TestGraphContext(RefreshableGraph refreshableGraph,
                        ApplicationGraphDraw graphDraw,
                        KoraAppGraph koraAppGraph) {
}
