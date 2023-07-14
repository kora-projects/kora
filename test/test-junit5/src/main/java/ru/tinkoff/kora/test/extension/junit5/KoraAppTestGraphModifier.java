package ru.tinkoff.kora.test.extension.junit5;

import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;

import javax.annotation.Nonnull;

/**
 * Is useful when {@link ApplicationGraphDraw} is needed to be modified before test execution
 */
public interface KoraAppTestGraphModifier {

    /**
     * @return Kora Graph Modifier builder used to add or replace nodes inside {@link ApplicationGraphDraw}
     */
    @Nonnull
    KoraGraphModification graph();
}
