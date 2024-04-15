package ru.tinkoff.kora.bpmn.camunda7.engine;

import org.camunda.bpm.engine.ArtifactFactory;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.DefaultArtifactFactory;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class KoraArtifactFactory implements ArtifactFactory {

    private final ArtifactFactory defaultArtifactFactory = new DefaultArtifactFactory();
    private final Map<String, Object> componentByKey;

    public KoraArtifactFactory(List<JavaDelegate> delegates) {
        this.componentByKey = new HashMap<>();
        for (JavaDelegate delegate : delegates) {
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
