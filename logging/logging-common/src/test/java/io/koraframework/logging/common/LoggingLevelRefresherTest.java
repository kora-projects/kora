package io.koraframework.logging.common;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.Lifecycle;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggingLevelRefresherTest {

    record TestLoggingConfig(Map<String, String> levels) implements LoggingConfig {}

    static final class RecordingApplier implements LoggingLevelApplier {
        final Map<String, String> levels = new HashMap<>();
        int resets = 0;

        @Override
        public void apply(String logName, String logLevel) {levels.put(logName, logLevel);}

        @Override
        public void reset() {levels.clear(); resets++;}
    }

    @Test
    void rejectedRefreshDoesNotApplyLevelsAndNextRefreshAppliesCommittedConfig() throws Exception {
        var levels = new AtomicReference<>(Map.of("test", "INFO"));
        var broken = new AtomicBoolean(false);
        var applier = new RecordingApplier();

        var draw = new ApplicationGraphDraw(LoggingLevelRefresherTest.class);
        var source = draw.addNode(Object.class, null, null, List.of(), List.of(), List.of(), _ -> new Object());
        var config = draw.<LoggingConfig>addNode(LoggingConfig.class, null, null, List.of(source), List.of(source), List.of(), _ -> new TestLoggingConfig(levels.get()));
        var applierNode = draw.<LoggingLevelApplier>addNode(LoggingLevelApplier.class, null, null, List.of(), List.of(), List.of(), _ -> applier);
        draw.addNode(LoggingLevelRefresher.class, null, null, List.of(config, applierNode), List.of(applierNode), List.of(),
            g -> new LoggingLevelRefresher(g.valueOf(config), g.get(applierNode)));
        // another component that rejects the new config
        draw.addNode(Lifecycle.class, null, null, List.of(config), List.of(config), List.of(), _ -> new Lifecycle() {
            @Override
            public void init() {
                if (broken.get()) {
                    throw new IllegalStateException("invalid config");
                }
            }

            @Override
            public void release() {}
        });
        var graph = draw.init();
        try {
            assertThat(applier.levels).containsExactly(Map.entry("test", "INFO"));

            levels.set(Map.of("test", "DEBUG"));
            broken.set(true);
            assertThatThrownBy(() -> graph.refresh(source)).hasMessage("invalid config");
            assertThat(applier.levels).containsExactly(Map.entry("test", "INFO"));

            levels.set(Map.of("test", "INFO"));
            broken.set(false);
            graph.refresh(source);
            assertThat(applier.levels).containsExactly(Map.entry("test", "INFO"));

            // a refresh with an equal config leaves the levels alone
            applier.levels.put("test", "WARN");
            var resets = applier.resets;
            graph.refresh(source);
            assertThat(applier.resets).isEqualTo(resets);
            assertThat(applier.levels).containsExactly(Map.entry("test", "WARN"));

            levels.set(Map.of("test", "DEBUG"));
            graph.refresh(source);
            assertThat(applier.levels).containsExactly(Map.entry("test", "DEBUG"));
        } finally {
            graph.release();
        }
    }
}
