package io.koraframework.bpmn.operaton.engine;

import org.operaton.bpm.engine.ArtifactFactory;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class KoraArtifactFactoryTests {

    private static final class SimpleDelegate implements JavaDelegate {

        @Override
        public void execute(DelegateExecution execution) {
            // do nothing
        }
    }

    @Test
    void getByCanonicalName() {
        ArtifactFactory artifactFactory = new KoraArtifactFactory(delegate -> delegate, List.of(), List.of(new SimpleDelegate()));
        assertInstanceOf(SimpleDelegate.class, artifactFactory.getArtifact(SimpleDelegate.class));
    }
}
