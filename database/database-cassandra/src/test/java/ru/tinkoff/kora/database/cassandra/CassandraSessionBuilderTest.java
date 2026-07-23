package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.internal.core.config.typesafe.DefaultProgrammaticDriverConfigLoaderBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.COALESCER_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;

class CassandraSessionBuilderTest {

    @Test
    void coalescerRescheduleIntervalIsAppliedToDriverConfig() {
        var loaderBuilder = new DefaultProgrammaticDriverConfigLoaderBuilder();

        new CassandraSessionBuilder().setAdvancedOptions(loaderBuilder, advancedWithCoalescer(Duration.ofMillis(15)));

        var profile = loaderBuilder.build().getInitialConfig().getDefaultProfile();
        assertThat(profile.getDuration(COALESCER_INTERVAL)).isEqualTo(Duration.ofMillis(15));
    }

    @Test
    void coalescerRescheduleIntervalIsNotOverriddenWhenNotConfigured() {
        var driverDefault = new DefaultProgrammaticDriverConfigLoaderBuilder()
            .build()
            .getInitialConfig()
            .getDefaultProfile()
            .getDuration(COALESCER_INTERVAL);
        var loaderBuilder = new DefaultProgrammaticDriverConfigLoaderBuilder();

        new CassandraSessionBuilder().setAdvancedOptions(loaderBuilder, advancedWithCoalescer(null));

        var profile = loaderBuilder.build().getInitialConfig().getDefaultProfile();
        assertThat(profile.getDuration(COALESCER_INTERVAL)).isEqualTo(driverDefault);
    }

    private static CassandraConfig.Advanced advancedWithCoalescer(Duration rescheduleInterval) {
        var coalescer = (rescheduleInterval == null)
            ? null
            : new $CassandraConfig_Advanced_CoalescerConfig_ConfigValueExtractor.CoalescerConfig_Impl(rescheduleInterval);

        return new $CassandraConfig_Advanced_ConfigValueExtractor.Advanced_Impl(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new $CassandraConfig_Advanced_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(
                new $CassandraConfig_Advanced_MetricsConfig_IdGenerator_ConfigValueExtractor.IdGenerator_Impl("DefaultMetricIdGenerator", null),
                null,
                null,
                false
            ),
            null,
            null,
            null,
            null,
            null,
            null,
            coalescer,
            null,
            null
        );
    }
}
