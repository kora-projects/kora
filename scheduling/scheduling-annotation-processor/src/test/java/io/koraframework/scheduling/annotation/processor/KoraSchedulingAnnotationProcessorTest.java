package io.koraframework.scheduling.annotation.processor;

import io.koraframework.scheduling.annotation.processor.controller.*;
import org.junit.jupiter.api.Test;
import org.quartz.DisallowConcurrentExecution;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import io.koraframework.scheduling.annotation.processor.controller.*;

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
                @io.koraframework.scheduling.quartz.ScheduleWithTrigger(TestClass.class)
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
        var cl = TestUtils.annotationProcess(clazz, new KoraSchedulingAnnotationProcessor(), new ConfigParserAnnotationProcessor());
        var module = cl.loadClass(clazz.getPackageName() + ".$" + clazz.getSimpleName() + "_SchedulingModule");
        return new ProcessResult(cl, module);
    }
}
