package io.koraframework.scheduling.db;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.ConfigValueOrigin;
import io.koraframework.config.common.ConfigValuePath;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.config.common.origin.SimpleConfigOrigin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class KoraDbConfigTests {

    @Test
    @SuppressWarnings("unchecked")
    void dbConfigExtractedFromSchedulingDbPath() {
        var expected = new SchedulingDbConfig() {};
        var mockExtractor = Mockito.mock(ConfigValueExtractor.class);
        var mockConfig = Mockito.mock(Config.class);
        ConfigValue value = new ConfigValue.ObjectValue(
            ConfigValueOrigin.of(new SimpleConfigOrigin("test"), ConfigValuePath.ROOT),
            java.util.Map.of()
        );

        when(mockConfig.get(anyString())).thenReturn(value);
        when(mockExtractor.extract(any())).thenReturn(expected);

        var module = new SchedulingDbModule() {};
        var config = module.schedulingDbConfig(mockConfig, mockExtractor);

        assertThat(config).isSameAs(expected);
        Mockito.verify(mockConfig).get("scheduling.db");
        Mockito.verify(mockExtractor).extract(value);
    }

    @Test
    void defaultConfigDoesNotInitializeTable() {
        var config = new SchedulingDbConfig() {};

        assertThat(config.initializeTable()).isFalse();
    }

    @Test
    void dbSchedulerLifecycleCreatedByModule() {
        var module = new SchedulingDbModule() {};
        var lifecycle = module.dbSchedulerLifecycle(
            Mockito.mock(DataSource.class),
            new SchedulingDbConfig() {},
            All.of(new TestValueOf<>(Mockito.mock(DbScheduledJob.class)))
        );

        assertThat(lifecycle).isNotNull();
    }

    private record TestValueOf<T>(T value) implements ValueOf<T> {
        @Override
        public T get() {
            return this.value;
        }
    }
}
