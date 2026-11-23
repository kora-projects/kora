package io.koraframework.camunda.engine.bpmn;

import io.koraframework.common.annotation.AopProxy;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.impl.juel.SimpleContext;
import org.camunda.bpm.impl.juel.jakarta.el.ELResolver;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class KoraELResolverTests {

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
        public void execute(DelegateExecution delegateExecution) {

        }
    }

    public static class ProxiedDelegate implements JavaDelegate {
        @Override
        public void execute(DelegateExecution execution) {
            // do nothing
        }
    }

    // shape of the subclass Kora AOP generates for a component with aspects
    @AopProxy
    public static final class $ProxiedDelegate__AopProxy extends ProxiedDelegate {}

    @Test
    void getByCanonicalName() {
        ELResolver resolver = new KoraELResolver(delegate -> delegate, List.of(), List.of(new SimpleDelegate()));
        assertInstanceOf(SimpleDelegate.class, resolver.getValue(new SimpleContext(), null, SimpleDelegate.class.getCanonicalName()));
    }

    @Test
    void getBySimpleName() {
        ELResolver resolver = new KoraELResolver(delegate -> delegate, List.of(), List.of(new SimpleDelegate()));
        assertInstanceOf(SimpleDelegate.class, resolver.getValue(new SimpleContext(), null, SimpleDelegate.class.getSimpleName()));
    }

    @Test
    void getByKey() {
        ELResolver resolver = new KoraELResolver(delegate -> delegate, List.of(new SimpleKoraDelegate()), List.of());
        assertInstanceOf(SimpleKoraDelegate.class, resolver.getValue(new SimpleContext(), null, "key"));
    }

    @Test
    void getAopProxyByUserClassNames() {
        var proxy = new $ProxiedDelegate__AopProxy();
        ELResolver resolver = new KoraELResolver(delegate -> delegate, List.of(), List.of(proxy));
        assertSame(proxy, resolver.getValue(new SimpleContext(), null, ProxiedDelegate.class.getSimpleName()));
        assertSame(proxy, resolver.getValue(new SimpleContext(), null, ProxiedDelegate.class.getCanonicalName()));
    }
}
