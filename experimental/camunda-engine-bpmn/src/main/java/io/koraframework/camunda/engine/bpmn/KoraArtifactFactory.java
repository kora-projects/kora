package io.koraframework.camunda.engine.bpmn;

import io.koraframework.common.annotation.AopProxy;
import org.camunda.bpm.engine.ArtifactFactory;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.DefaultArtifactFactory;

import java.util.HashMap;
import java.util.Map;

public final class KoraArtifactFactory implements ArtifactFactory {

    private final ArtifactFactory defaultArtifactFactory = new DefaultArtifactFactory();
    private final Map<String, Object> componentByKey;

    public KoraArtifactFactory(KoraDelegateWrapperFactory wrapperFactory,
                               Iterable<KoraDelegate> koraDelegates,
                               Iterable<JavaDelegate> javaDelegates) {
        this.componentByKey = new HashMap<>();
        for (JavaDelegate delegate : javaDelegates) {
            JavaDelegate wrapped = wrapperFactory.wrap(delegate);
            this.componentByKey.put(delegateClass(delegate).getCanonicalName(), wrapped);
        }

        for (JavaDelegate delegate : koraDelegates) {
            JavaDelegate wrapped = wrapperFactory.wrap(delegate);
            this.componentByKey.put(delegateClass(delegate).getCanonicalName(), wrapped);
        }
    }

    @Override
    public <T> T getArtifact(Class<T> clazz) {
        @SuppressWarnings("unchecked")
        var artifact = (T) componentByKey.get(clazz.getCanonicalName());
        if (artifact != null) {
            return artifact;
        }

        return defaultArtifactFactory.getArtifact(clazz);
    }

    /**
     * Kora AOP subclasses components that have aspects; delegates are looked up by the user's class, not the proxy.
     */
    static Class<?> delegateClass(Object delegate) {
        var type = delegate.getClass();
        return type.isAnnotationPresent(AopProxy.class) ? type.getSuperclass() : type;
    }
}
