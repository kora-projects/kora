package ru.tinkoff.kora.bpmn.camunda7.engine;

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

    private static class SimpleCamundaDelegate implements CamundaDelegate {
        @NotNull
        @Override
        public String key() {
            return "key";
        }
    }

    @Test
    void getByCanonicalName() {
        Resolver resolver = new KoraResolverFactory(List.of(), List.of(new SimpleDelegate()));
        assertInstanceOf(SimpleDelegate.class, resolver.get(SimpleDelegate.class.getCanonicalName()));
    }

    @Test
    void getBySimpleName() {
        Resolver resolver = new KoraResolverFactory(List.of(), List.of(new SimpleDelegate()));
        assertInstanceOf(SimpleDelegate.class, resolver.get(SimpleDelegate.class.getSimpleName()));
    }

    @Test
    void getByKey() {
        Resolver resolver = new KoraResolverFactory(List.of(new SimpleCamundaDelegate()), List.of());
        assertInstanceOf(SimpleCamundaDelegate.class, resolver.get("key"));
    }
}
