package ru.tinkoff.kora.test.extension.junit5;

import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;

/**
 * Is useful when {@link ApplicationGraphDraw} is needed to be modified before test execution
 */
public interface KoraAppTestGraphModifier {

    /**
     * @return Kora Graph Modifier builder used to add or replace nodes inside {@link ApplicationGraphDraw}
     */
    KoraGraphModification graph();
}
