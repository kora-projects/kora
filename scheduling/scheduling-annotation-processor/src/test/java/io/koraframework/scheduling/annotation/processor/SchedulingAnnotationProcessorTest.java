package io.koraframework.scheduling.annotation.processor;

import io.koraframework.scheduling.annotation.processor.controller.*;
import org.junit.jupiter.api.Test;
import org.quartz.DisallowConcurrentExecution;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.config.annotation.processor.processor.ConfigParserAnnotationProcessor;

import java.util.List;

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
    public void testScheduledQuartzDisallowConcurrentExecutionOnClass() {
        var cr = compile(List.of(new SchedulingAnnotationProcessor()), """
            @org.quartz.DisallowConcurrentExecution
            public class TestClass {
                @io.koraframework.scheduling.quartz.ScheduleWithTrigger(TestClass.class)
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
            public class TestClass {
                @io.koraframework.scheduling.quartz.ScheduleWithTrigger(TestClass.class)
                @io.koraframework.scheduling.quartz.DisallowConcurrentExecution
                public void job() {}
            }
            """);
        cr.assertSuccess();
        var clazz = cr.loadClass("$TestClass_job_Job");
        assertThat(clazz).hasAnnotation(DisallowConcurrentExecution.class);
    }


    private record ProcessResult(ClassLoader cl, Class<?> module) {}

    private ProcessResult process(Class<?> clazz) throws Exception {
        var cl = TestUtils.annotationProcess(clazz, new SchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor());
        var module = cl.loadClass(clazz.getPackageName() + ".$" + clazz.getSimpleName() + "_SchedulingModule");
        return new ProcessResult(cl, module);
    }
}
