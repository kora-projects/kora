package ru.tinkoff.kora.scheduling.annotation.processor;

import org.junit.jupiter.api.Test;
import org.quartz.DisallowConcurrentExecution;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import ru.tinkoff.kora.scheduling.annotation.processor.controller.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KoraSchedulingAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
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
    void testScheduledWithTrigger() throws Exception {
        process(ScheduledWithTrigger.class);
    }

    @Test
    void testScheduledWithCron() throws Exception {
        process(ScheduledWithCron.class);
    }

    @Test
    public void testDisallowConcurrentExecutionOnClass() {
        var cr = compile(List.of(new KoraSchedulingAnnotationProcessor()), """
            @org.quartz.DisallowConcurrentExecution
            public class TestClass {
                @ru.tinkoff.kora.scheduling.quartz.ScheduleWithTrigger(@Tag(TestClass.class))
                public void job() {}
            }
            """);
        cr.assertSuccess();
        var clazz = cr.loadClass("$TestClass_job_Job");
        assertThat(clazz).hasAnnotation(DisallowConcurrentExecution.class);
    }

    @Test
    public void testDisallowConcurrentExecutionOnMethod() {
        var cr = compile(List.of(new KoraSchedulingAnnotationProcessor()), """
            public class TestClass {
                @ru.tinkoff.kora.scheduling.quartz.ScheduleWithTrigger(@Tag(TestClass.class))
                @ru.tinkoff.kora.scheduling.quartz.DisallowConcurrentExecution
                public void job() {}
            }
            """);
        cr.assertSuccess();
        var clazz = cr.loadClass("$TestClass_job_Job");
        assertThat(clazz).hasAnnotation(DisallowConcurrentExecution.class);
    }


    private record ProcessResult(ClassLoader cl, Class<?> module) {}

    private ProcessResult process(Class<?> clazz) throws Exception {
        var cl = TestUtils.annotationProcess(clazz, new KoraSchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor());
        var module = cl.loadClass(clazz.getPackageName() + ".$" + clazz.getSimpleName() + "_SchedulingModule");
        return new ProcessResult(cl, module);
    }
}
