package io.koraframework.camunda.engine.bpmn;

import io.koraframework.common.annotation.AopProxy;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.scripting.engine.Resolver;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    @Test
    void getAopProxyByUserClassNames() {
        var proxy = new $ProxiedDelegate__AopProxy();
        Resolver resolver = new KoraResolverFactory(delegate -> delegate, List.of(), List.of(proxy));
        assertSame(proxy, resolver.get(ProxiedDelegate.class.getSimpleName()));
        assertSame(proxy, resolver.get(ProxiedDelegate.class.getCanonicalName()));
    }
}
