package ru.tinkoff.kora.test.extension.junit5;

import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

interface GraphModification extends Consumer<ApplicationGraphDraw> {

    @Nonnull
    GraphCandidate candidate();
}
