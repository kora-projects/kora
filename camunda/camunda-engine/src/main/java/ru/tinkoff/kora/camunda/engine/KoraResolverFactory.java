package ru.tinkoff.kora.camunda.engine;

import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.delegate.VariableScope;
import org.camunda.bpm.engine.impl.scripting.engine.Resolver;
import org.camunda.bpm.engine.impl.scripting.engine.ResolverFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KoraResolverFactory implements ResolverFactory, Resolver {

    private final Map<String, Object> componentByKey;

    public KoraResolverFactory(List<KoraDelegate> koraDelegates,
                               List<JavaDelegate> javaDelegates) {
        this.componentByKey = new HashMap<>();
        for (JavaDelegate delegate : javaDelegates) {
            this.componentByKey.put(delegate.getClass().getSimpleName(), delegate);
            this.componentByKey.put(delegate.getClass().getCanonicalName(), delegate);
        }

        for (KoraDelegate delegate : koraDelegates) {
            this.componentByKey.put(delegate.key(), delegate);
            this.componentByKey.put(delegate.getClass().getSimpleName(), delegate);
            this.componentByKey.put(delegate.getClass().getCanonicalName(), delegate);
        }
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof String k && this.componentByKey.containsKey(k);
    }

    @Override
    public Object get(Object key) {
        if (key instanceof String k) {
            return componentByKey.get(k);
        }
        return null;
    }

    @Override
    public Set<String> keySet() {
        return componentByKey.keySet();
    }

    @Override
    public Resolver createResolver(VariableScope variableScope) {
        return this;
    }
}
