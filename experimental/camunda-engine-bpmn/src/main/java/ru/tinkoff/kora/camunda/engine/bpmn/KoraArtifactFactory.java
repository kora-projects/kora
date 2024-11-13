package ru.tinkoff.kora.camunda.engine.bpmn;

import org.camunda.bpm.engine.ArtifactFactory;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.DefaultArtifactFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KoraArtifactFactory implements ArtifactFactory {

    private final ArtifactFactory defaultArtifactFactory = new DefaultArtifactFactory();
    private final Map<String, Object> componentByKey;

    public KoraArtifactFactory(KoraDelegateWrapperFactory wrapperFactory,
                               List<KoraDelegate> koraDelegates,
                               List<JavaDelegate> javaDelegates) {
        this.componentByKey = new HashMap<>();
        for (JavaDelegate delegate : javaDelegates) {
            JavaDelegate wrapped = wrapperFactory.wrap(delegate);
            this.componentByKey.put(delegate.getClass().getCanonicalName(), wrapped);
        }

        for (JavaDelegate delegate : koraDelegates) {
            JavaDelegate wrapped = wrapperFactory.wrap(delegate);
            this.componentByKey.put(delegate.getClass().getCanonicalName(), wrapped);
        }
    }

    @Override
    public <T> T getArtifact(Class<T> clazz) {
        var artifact = (T) componentByKey.get(clazz.getCanonicalName());
        if (artifact != null) {
            return artifact;
        }

        return defaultArtifactFactory.getArtifact(clazz);
    }
}
