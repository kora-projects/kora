package ru.tinkoff.kora.camunda.engine;

import org.camunda.bpm.engine.impl.el.JuelExpressionManager;
import org.camunda.bpm.engine.impl.el.VariableContextElResolver;
import org.camunda.bpm.engine.impl.el.VariableScopeElResolver;
import org.camunda.bpm.engine.test.mock.MockElResolver;
import org.camunda.bpm.impl.juel.jakarta.el.*;

public final class KoraExpressionManager extends JuelExpressionManager {

    private final ELResolver koraELResolver;

    public KoraExpressionManager(ELResolver koraELResolver) {
        this.koraELResolver = koraELResolver;
    }

    @Override
    protected ELResolver createElResolver() {
        CompositeELResolver resolver = new CompositeELResolver();
        resolver.add(koraELResolver);
        resolver.add(new VariableScopeElResolver());
        resolver.add(new VariableContextElResolver());
        resolver.add(new MockElResolver());
        resolver.add(new ArrayELResolver());
        resolver.add(new ListELResolver());
        resolver.add(new MapELResolver());
        return resolver;
    }
}
