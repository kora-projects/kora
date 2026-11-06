package io.koraframework.scheduling.quartz;

import com.typesafe.config.ConfigFactory;
import io.koraframework.config.common.mapper.PropertiesConfigValueMapper;
import io.koraframework.config.common.origin.SimpleConfigOrigin;
import io.koraframework.config.hocon.HoconConfigFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzHoconPropertiesTest {

    @Test
    void unquotedNumberAndBooleanPropertiesAreApplied() throws Exception {
        var hocon = ConfigFactory.parseString("""
            scheduling.quartz.properties {
              org.quartz.scheduler.instanceName = "hocon-scheduler"
              org.quartz.threadPool.threadCount = 3
              org.quartz.threadPool.makeThreadsDaemons = true
            }
            """).resolve();
        var config = HoconConfigFactory.fromHocon(new SimpleConfigOrigin("test"), hocon);

        var module = new QuartzModule() {};
        var properties = module.quartzProperties(config, new PropertiesConfigValueMapper());
        var scheduler = module.koraQuartzScheduler(new KoraQuartzJobFactory(List.of()), properties, new QuartzConfig() {});
        scheduler.init();
        try {
            assertThat(scheduler.value().getSchedulerName()).isEqualTo("hocon-scheduler");
            assertThat(scheduler.value().getMetaData().getThreadPoolSize()).isEqualTo(3);
            assertThat(Thread.getAllStackTraces().keySet())
                .filteredOn(t -> t.getName().startsWith("hocon-scheduler_Worker-"))
                .hasSize(3)
                .allMatch(Thread::isDaemon);
        } finally {
            scheduler.release();
        }
    }
}
