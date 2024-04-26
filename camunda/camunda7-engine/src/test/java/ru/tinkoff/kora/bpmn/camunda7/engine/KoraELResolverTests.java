package ru.tinkoff.kora.bpmn.camunda7.engine;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.impl.juel.SimpleContext;
import org.camunda.bpm.impl.juel.jakarta.el.ELResolver;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class KoraELResolverTests {

    private static final class SimpleDelegate implements JavaDelegate {

        @Override
        public void execute(DelegateExecution execution) {
            // do nothing
        }
    }

    private static class SimpleCamunda7Delegate implements Camunda7Delegate {
        @NotNull
        @Override
        public String key() {
            return "key";
        }

        @Override
        public void execute(DelegateExecution delegateExecution) {

        }
    }

    @Test
    void getByCanonicalName() {
        ELResolver resolver = new KoraELResolver(List.of(), List.of(new SimpleDelegate()));
        assertInstanceOf(SimpleDelegate.class, resolver.getValue(new SimpleContext(), null, SimpleDelegate.class.getCanonicalName()));
    }

    @Test
    void getBySimpleName() {
        ELResolver resolver = new KoraELResolver(List.of(), List.of(new SimpleDelegate()));
        assertInstanceOf(SimpleDelegate.class, resolver.getValue(new SimpleContext(), null, SimpleDelegate.class.getSimpleName()));
    }

    @Test
    void getByKey() {
        ELResolver resolver = new KoraELResolver(List.of(new SimpleCamunda7Delegate()), List.of());
        assertInstanceOf(SimpleCamunda7Delegate.class, resolver.getValue(new SimpleContext(), null, "key"));
    }
}
