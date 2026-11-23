package io.koraframework.camunda.engine.bpmn;

import io.koraframework.common.annotation.AopProxy;
import org.camunda.bpm.engine.ArtifactFactory;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class KoraArtifactFactoryTests {

    private static final class SimpleDelegate implements JavaDelegate {

        @Override
        public void execute(DelegateExecution execution) {
            // do nothing
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
        ArtifactFactory artifactFactory = new KoraArtifactFactory(delegate -> delegate, List.of(), List.of(new SimpleDelegate()));
        assertInstanceOf(SimpleDelegate.class, artifactFactory.getArtifact(SimpleDelegate.class));
    }

    @Test
    void getAopProxyByUserClass() {
        var proxy = new $ProxiedDelegate__AopProxy();
        ArtifactFactory artifactFactory = new KoraArtifactFactory(delegate -> delegate, List.of(), List.of(proxy));
        assertSame(proxy, artifactFactory.getArtifact(ProxiedDelegate.class));
    }
}
