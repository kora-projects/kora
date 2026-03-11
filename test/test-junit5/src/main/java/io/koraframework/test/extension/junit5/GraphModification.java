package io.koraframework.test.extension.junit5;

import io.koraframework.application.graph.ApplicationGraphDraw;

import java.util.function.Consumer;

interface GraphModification extends Consumer<ApplicationGraphDraw> {

    GraphCandidate candidate();
}
