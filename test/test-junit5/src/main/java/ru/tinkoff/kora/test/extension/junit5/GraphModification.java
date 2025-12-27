package ru.tinkoff.kora.test.extension.junit5;

import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;

import java.util.function.Consumer;

interface GraphModification extends Consumer<ApplicationGraphDraw> {

    GraphCandidate candidate();
}
