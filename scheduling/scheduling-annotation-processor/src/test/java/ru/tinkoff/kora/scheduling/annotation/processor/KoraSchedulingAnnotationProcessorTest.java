package ru.tinkoff.kora.scheduling.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.scheduling.annotation.processor.controller.*;

class KoraSchedulingAnnotationProcessorTest {
    @Test
    void test() throws Exception {
        process(ScheduledJdkAtFixedRateTest.class);
        process(ScheduledJdkAtFixedDelayTest.class);
        process(ScheduledJdkOnceTest.class);
        process(ScheduledWithTrigger.class);
        process(ScheduledWithCron.class);
    }

    private record ProcessResult(ClassLoader cl, Class<?> module) {}

    private ProcessResult process(Class<?> clazz) throws Exception {
        var cl = TestUtils.annotationProcess(clazz, new KoraSchedulingAnnotationProcessor());
        var module = cl.loadClass(clazz.getPackageName() + ".$" + clazz.getSimpleName() + "_SchedulingModule");
        return new ProcessResult(cl, module);
    }
}
