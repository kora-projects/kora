package ru.tinkoff.kora.camunda.engine;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.scripting.engine.Resolver;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class KoraResolverFactoryTests {

    private static final class SimpleDelegate implements JavaDelegate {

        @Override
        public void execute(DelegateExecution execution) {
            // do nothing
        }
    }

    private static class SimpleKoraDelegate implements KoraDelegate {
        @NotNull
        @Override
        public String key() {
            return "key";
        }

        @Override
        public void execute(DelegateExecution delegateExecution) throws Exception {

        }
    }

    @Test
    void getByCanonicalName() {
        Resolver resolver = new KoraResolverFactory(delegate -> delegate, List.of(), List.of(new SimpleDelegate()));
        assertInstanceOf(SimpleDelegate.class, resolver.get(SimpleDelegate.class.getCanonicalName()));
    }

    @Test
    void getBySimpleName() {
        Resolver resolver = new KoraResolverFactory(delegate -> delegate, List.of(), List.of(new SimpleDelegate()));
        assertInstanceOf(SimpleDelegate.class, resolver.get(SimpleDelegate.class.getSimpleName()));
    }

    @Test
    void getByKey() {
        Resolver resolver = new KoraResolverFactory(delegate -> delegate, List.of(new SimpleKoraDelegate()), List.of());
        assertInstanceOf(SimpleKoraDelegate.class, resolver.get("key"));
    }
}
