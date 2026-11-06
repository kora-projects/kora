package io.koraframework.scheduling.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.config.common.util.ConfigMappingUtils;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.scheduling.annotation.processor.db.ScheduledDbTest;
import io.koraframework.scheduling.annotation.processor.jdk.ScheduledJdkAtFixedDelayTest;
import io.koraframework.scheduling.annotation.processor.jdk.ScheduledJdkAtFixedRateTest;
import io.koraframework.scheduling.annotation.processor.jdk.ScheduledJdkOnceTest;
import io.koraframework.scheduling.annotation.processor.jdk.ScheduledJdkWithCronTest;
import io.koraframework.scheduling.annotation.processor.quartz.ScheduledQuartzWithCron;
import io.koraframework.scheduling.annotation.processor.quartz.ScheduledQuartzWithTrigger;
import io.koraframework.scheduling.common.SchedulingJobConfig;
import io.koraframework.scheduling.common.SchedulingModule;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetryFactory;
import io.koraframework.scheduling.common.telemetry.impl.NoopSchedulingTelemetry;
import io.koraframework.scheduling.db.scheduler.KoraDbScheduler;
import io.koraframework.scheduling.db.scheduler.job.DbSchedulerJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.quartz.DisallowConcurrentExecution;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    @Test
    void testScheduledJdkAtFixedRateTest() throws Exception {
        process(ScheduledJdkAtFixedRateTest.class);
    }

    @Test
    void testScheduledJdkAtFixedDelayTest() throws Exception {
        process(ScheduledJdkAtFixedDelayTest.class);
    }

    @Test
    void testScheduledJdkOnceTest() throws Exception {
        process(ScheduledJdkOnceTest.class);
    }

    @Test
    void testScheduledJdkWithCronTest() throws Exception {
        process(ScheduledJdkWithCronTest.class);
    }

    @Test
    void testScheduledQuartzWithTrigger() throws Exception {
        process(ScheduledQuartzWithTrigger.class);
    }

    @Test
    void testScheduledQuartzWithCron() throws Exception {
        process(ScheduledQuartzWithCron.class);
    }

    @Test
    void testScheduledDb() throws Exception {
        process(ScheduledDbTest.class);
    }

    @Test
    public void testJobOfConditionalComponentIsConditional() {
        var cr = compile(List.of(new SchedulingAnnotationProcessor()), """
            @io.koraframework.common.annotation.Conditional(tag = TestClass.class)
            public class TestClass {
                @io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)
                public void jdk() {}

                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass.class)
                public void quartz() {}
            }
            """);
        cr.assertSuccess();
        var module = cr.loadClass("$TestClass_SchedulingModule");
        var jobs = java.util.Arrays.stream(module.getDeclaredMethods())
            .filter(m -> m.getName().endsWith("_Job"))
            .toList();
        assertThat(jobs).hasSize(2);
        for (var job : jobs) {
            var conditional = job.getAnnotation(io.koraframework.common.annotation.Conditional.class);
            assertThat(conditional).as(job.getName()).isNotNull();
            assertThat(conditional.tag().getSimpleName()).isEqualTo("TestClass");
        }
    }

    @Test
    public void testScheduledQuartzDisallowConcurrentExecutionOnClass() {
        var cr = compile(List.of(new SchedulingAnnotationProcessor()), """
            @org.quartz.DisallowConcurrentExecution
            public class TestClass {
                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass.class)
                public void job() {}
            }
            """);
        cr.assertSuccess();
        var clazz = cr.loadClass("$TestClass_job_Job");
        assertThat(clazz).hasAnnotation(DisallowConcurrentExecution.class);
    }

    @Test
    public void testScheduledQuartzDisallowConcurrentExecutionOnMethod() {
        var cr = compile(List.of(new SchedulingAnnotationProcessor()), """
            import io.koraframework.scheduling.quartz.annotation.DisallowConcurrentExecution;public class TestClass {
                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass.class)
                @DisallowConcurrentExecution
                public void job() {}
            }
            """);
        cr.assertSuccess();
        var clazz = cr.loadClass("$TestClass_job_Job");
        assertThat(clazz).hasAnnotation(DisallowConcurrentExecution.class);
    }

    @Test
    public void testScheduledDbJobIsResolvedTogetherWithDbScheduler() {
        compile(List.of(new KoraAppProcessor(), new SchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            @KoraApp
            public interface JobApplication extends io.koraframework.scheduling.db.scheduler.DbSchedulerModule, io.koraframework.config.common.mapper.ConfigValueMapperModule {
                default io.koraframework.config.common.Config config() { return null; }
            
                default javax.sql.DataSource dataSource() { return null; }
            }
            """, """
            @Component
            public class SomeJob {
                @io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithFixedDelay(delay = 1000)
                public void run() {}
            }
            """);
        compileResult.assertSuccess();

        var draw = loadGraphDraw("JobApplication");
        assertThat(draw.findNodeByType(KoraDbScheduler.class)).isNotNull();
    }

    @Test
    public void testScheduledQuartzWithCronConfigOnSeveralMethods() {
        var cr = compile(List.of(new SchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            public class TestClass {
                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron(value = "0 * * * * ?", config = "jobs.first")
                public void first() {}

                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron(value = "0 0 * * * ?", config = "jobs.second")
                public void second() {}
            }
            """);
        cr.assertSuccess();
    }

    @Test
    public void testScheduledDbDefaultNameIsCanonicalName() throws Exception {
        var cr = compile(List.of(new SchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            public class TestClass {
                @io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithFixedDelay(delay = 1000)
                public void job() {}
            }
            """);
        cr.assertSuccess();

        var module = cr.loadClass("$TestClass_SchedulingModule");
        var componentMethod = Arrays.stream(module.getMethods())
            .filter(m -> m.getReturnType() == DbSchedulerJob.class)
            .findFirst()
            .orElseThrow();
        var moduleInstance = Proxy.newProxyInstance(module.getClassLoader(), new Class<?>[]{module}, (proxy, method, args) -> InvocationHandler.invokeDefault(proxy, method, args));
        SchedulingTelemetryFactory telemetryFactory = (schedulerType, configPath, telemetryConfig, jobClass, jobMethod) -> NoopSchedulingTelemetry.INSTANCE;

        var job = (DbSchedulerJob) componentMethod.invoke(moduleInstance, telemetryFactory, null);

        assertThat(job.task().getName()).isEqualTo(testPackage() + ".TestClass#job");
    }

    @Test
    public void testScheduledDbNameLongerThanColumnIsRejected() {
        var cr = compile(List.of(new SchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            public class TestClass {
                @io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithFixedDelay(delay = 1000, name = "%s")
                public void job() {}
            }
            """.formatted("a".repeat(351)));

        assertThat(cr.isFailed()).isTrue();
        assertThat(cr.errors()).anyMatch(d -> d.getMessage(null).contains("maximum is 350"));
    }

    @Test
    public void testScheduledDbConfigHasNoName() {
        var cr = compile(List.of(new SchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            public class TestClass {
                @io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithFixedDelay(delay = 1000, config = "jobs.job")
                public void job() {}
            }
            """);
        cr.assertSuccess();

        var config = cr.loadClass("$TestClass_job_Config");
        assertThat(config.getMethods()).noneMatch(m -> m.getName().equals("name"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron       | 0 0 12 L * ?   | ''                 | JDK scheduler expects
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron       | 0 0 24 * * ?   | jobs.job           | JDK scheduler expects
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron | 0 0 12 * * *   | ''                 | Quartz expects
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron | 0 0 12 * *     | jobs.job           | Quartz expects
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron | 0 0 12 * *   | ''                 | database scheduler expects
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron | 0 0 12 ? * 8 | jobs.job           | database scheduler expects
        """)
    public void testInvalidCronIsRejected(String annotation, String cron, String config, String format) {
        var cr = compile(List.of(new SchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            public class TestClass {
                @%s(value = "%s", config = "%s")
                public void job() {}
            }
            """.formatted(annotation, cron, config));

        assertThat(cr.isFailed()).isTrue();
        assertThat(cr.errors()).anySatisfy(d -> assertThat(d.getMessage(null))
            .contains("Invalid CRON expression '" + cron + "'")
            .contains("TestClass#job()")
            .contains(format)
            .contains("┌───────────── second (0-59")
            .contains("│ │ │ │ │ ┌───────────── day of the week")
            .contains("Examples:")
            .contains("runs every day at 15:00")
            .contains("runs every hour from 9:00 through 17:00 on weekdays"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron         | 0 0 12 * * ?
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron         | 0 0 * * *
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron   | 0 0 12 ? * MON#2
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron   | 0 0 12 L * ? 2030
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron | 0 0 12 * * MON-FRI
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron | @daily
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron | -
        """)
    public void testValidCronIsAccepted(String annotation, String cron) {
        var cr = compile(List.of(new SchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            public class TestClass {
                @%s("%s")
                public void job() {}
            }
            """.formatted(annotation, cron));

        cr.assertSuccess();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
        true
        false
        """)
    public void testCronJobsUseTaggedTimeZone(boolean zoneComponent) {
        var zone = zoneComponent
            ? "@Tag(io.koraframework.scheduling.common.SchedulingModule.class) default java.time.ZoneId zone() { return java.time.ZoneId.of(\"Europe/Moscow\"); }"
            : "";
        compile(List.of(new KoraAppProcessor(), new SchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            @KoraApp
            public interface JobApplication extends io.koraframework.scheduling.jdk.SchedulingJdkModule,
                io.koraframework.scheduling.quartz.QuartzModule,
                io.koraframework.scheduling.db.scheduler.DbSchedulerModule,
                io.koraframework.config.common.mapper.ConfigValueMapperModule {
                default io.koraframework.config.common.Config config() { return null; }

                default javax.sql.DataSource dataSource() { return null; }

                %s
            }
            """.formatted(zone), """
            @Component
            public class SomeJobs {
                @io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron("0 0 15 * * ?")
                public void jdk() {}

                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron("0 0 15 * * ?")
                public void quartz() {}

                @io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron("0 0 15 * * *")
                public void db() {}
            }
            """);
        compileResult.assertSuccess();

        var draw = loadGraphDraw("JobApplication");
        assertThat(draw.findNodesByType(ZoneId.class, SchedulingModule.class)).hasSize(zoneComponent ? 1 : 0);
    }

    @Test
    public void testJobConfigCanDisableJob() throws Exception {
        var cr = compile(List.of(new SchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            public class TestClass {
                @io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithFixedDelay(delay = 1000, config = "jobs.job")
                public void job() {}
            }
            """);
        cr.assertSuccess();

        ConfigValueMapper<Object> durationMapper = value -> java.time.Duration.ofSeconds(1);
        var telemetryConfig = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SchedulingJobConfig.JobTelemetryConfig.class}, (proxy, method, args) -> null);
        ConfigValueMapper<Object> telemetryMapper = value -> telemetryConfig;
        var mapper = (ConfigValueMapper<?>) cr.loadClass("$TestClass_job_Config_ConfigValueMapper").getConstructors()[0].newInstance(durationMapper, telemetryMapper);
        var disabled = mapper.map(ConfigMappingUtils.fromMap(Map.of("enabled", false)).root());
        var byDefault = mapper.map(ConfigMappingUtils.fromMap(Map.of()).root());

        assertThat(disabled.getClass().getMethod("enabled").invoke(disabled)).isEqualTo(false);
        assertThat(byDefault.getClass().getMethod("enabled").invoke(byDefault)).isEqualTo(true);
    }

    @Test
    public void testNestedTypesWithSameSimpleNameGetDistinctModules() {
        var cr = compile(List.of(new SchedulingAnnotationProcessor()), """
            public class OrderService {
                public static class Jobs {
                    @io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)
                    public void cleanup() {}
                }
            }
            """, """
            public class UserService {
                public static class Jobs {
                    @io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)
                    public void cleanup() {}
                }
            }
            """, """
            public class Jobs {
                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(Jobs.class)
                public void job() {}
            }
            """);
        cr.assertSuccess();

        assertThat(cr.loadClass("$OrderService_Jobs_SchedulingModule")).isInterface();
        assertThat(cr.loadClass("$UserService_Jobs_SchedulingModule")).isInterface();
        assertThat(cr.loadClass("$Jobs_SchedulingModule")).isInterface();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)         | private void job() {}
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)         | public void job(String arg) {}
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithFixedDelay(delay = 1000) | public void job(String arg) {}
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass.class)   | private void job() {}
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass.class)   | public void job(String arg) {}
        """)
    public void testInvalidScheduledMethodIsRejected(String annotation, String method) {
        var cr = compile(List.of(new SchedulingAnnotationProcessor()), """
            public class TestClass {
                @%s
                %s
            }
            """.formatted(annotation, method));

        assertThat(cr.isFailed()).isTrue();
        assertThat(cr.errors()).singleElement().satisfies(d -> assertThat(d.getMessage(null))
            .contains("Invalid scheduled method")
            .contains("TestClass#job"));
    }

    @Test
    public void testScheduledQuartzMethodWithJobExecutionContext() {
        var cr = compile(List.of(new SchedulingAnnotationProcessor()), """
            public class TestClass {
                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass.class)
                public void job(org.quartz.JobExecutionContext context) {}
            }
            """);
        cr.assertSuccess();
    }

    private record ProcessResult(ClassLoader cl, Class<?> module) {}

    private ProcessResult process(Class<?> clazz) throws Exception {
        var cl = TestUtils.annotationProcess(clazz, new SchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor());
        var module = cl.loadClass(clazz.getPackageName() + ".$" + clazz.getSimpleName() + "_SchedulingModule");
        return new ProcessResult(cl, module);
    }
}
