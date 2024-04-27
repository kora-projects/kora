package ru.tinkoff.kora.bpmn.camunda7.engine;

import org.camunda.bpm.engine.ArtifactFactory;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.DefaultArtifactFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KoraArtifactFactory implements ArtifactFactory {

    private final ArtifactFactory defaultArtifactFactory = new DefaultArtifactFactory();
    private final Map<String, Object> componentByKey;

    public KoraArtifactFactory(List<KoraDelegate> koraDelegates,
                               List<JavaDelegate> javaDelegates) {
        this.componentByKey = new HashMap<>();
        for (JavaDelegate delegate : javaDelegates) {
            this.componentByKey.put(delegate.getClass().getCanonicalName(), delegate);
        }

        for (JavaDelegate delegate : koraDelegates) {
            this.componentByKey.put(delegate.getClass().getCanonicalName(), delegate);
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
