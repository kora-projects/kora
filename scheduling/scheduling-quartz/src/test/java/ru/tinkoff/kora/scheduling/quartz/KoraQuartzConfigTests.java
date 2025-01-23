package ru.tinkoff.kora.scheduling.quartz;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.ConfigValuePath;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.impl.SimpleConfigValueOrigin;
import ru.tinkoff.kora.config.common.origin.SimpleConfigOrigin;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class KoraQuartzConfigTests {

    @Test
    void configDefaultsNotSame() throws Exception {
        var defaults = new Properties();
        try (var is = Thread.currentThread().getContextClassLoader().getResourceAsStream("org/quartz/quartz.properties")) {
            defaults.load(is);
        }

        var mockExtractor = Mockito.mock(ConfigValueExtractor.class);
        var mockConfig = Mockito.mock(Config.class);
        ConfigValue stringValue = new ConfigValue.StringValue(new SimpleConfigValueOrigin(new SimpleConfigOrigin(""), ConfigValuePath.ROOT), "some");

        when(mockExtractor.extract(any())).thenReturn(null);
        when(mockConfig.get(anyString())).thenReturn(stringValue);

        Properties properties = new QuartzModule() {}.quartzProperties(mockConfig, mockExtractor);

        assertNotEquals(defaults, properties);
    }

    @Test
    void quartzNameChanged() throws Exception {
        var mockExtractor = Mockito.mock(ConfigValueExtractor.class);
        var mockConfig = Mockito.mock(Config.class);
        ConfigValue stringValue = new ConfigValue.StringValue(new SimpleConfigValueOrigin(new SimpleConfigOrigin(""), ConfigValuePath.ROOT), "some");

        when(mockExtractor.extract(any())).thenReturn(null);
        when(mockConfig.get(anyString())).thenReturn(stringValue);

        QuartzModule quartzModule = new QuartzModule() {};
        Properties properties = quartzModule.quartzProperties(mockConfig, mockExtractor);
        KoraQuartzScheduler koraQuartzScheduler = quartzModule.koraQuartzScheduler(new KoraQuartzJobFactory(List.of()), properties, new SchedulingQuartzConfig() {});
        koraQuartzScheduler.init();

        assertNotEquals("DefaultQuartzScheduler", koraQuartzScheduler.value().getSchedulerName());
    }
}
