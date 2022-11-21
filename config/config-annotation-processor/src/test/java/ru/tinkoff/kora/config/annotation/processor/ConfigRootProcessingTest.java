package ru.tinkoff.kora.config.annotation.processor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.annotation.processor.cases.PojoConfigRoot;
import ru.tinkoff.kora.config.annotation.processor.cases.RecordConfigRoot;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigRootAnnotationProcessor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

class ConfigRootProcessingTest {

    @Test
    void testPojoRootConfig() throws Exception {
        var module = createModule(PojoConfigRoot.class);
        var methods = Arrays.stream(module.getDeclaredMethods())
            .map(method -> {
                var tagsPrefix = tagsPrefix(method);
                return tagsPrefix + method.getName() + ": " + method.getReturnType().getCanonicalName();
            })
            .filter(this::isNotJacocoInit)
            .collect(Collectors.toList());

        Assertions.assertThat(methods).containsOnly(
            "@PojoConfig pojoConfigValue: ru.tinkoff.kora.config.annotation.processor.cases.PojoConfig",
            "@RecordConfig recConfigValue: ru.tinkoff.kora.config.annotation.processor.cases.RecordConfig",
            "pojoConfigRoot: ru.tinkoff.kora.config.annotation.processor.cases.PojoConfigRoot"
        );
    }

    @Test
    void testRecordRootConfig() throws Exception {
        var module = createModule(RecordConfigRoot.class);
        var methods = Arrays.stream(module.getDeclaredMethods())
            .map(method -> {
                var tagsPrefix = tagsPrefix(method);
                return tagsPrefix + method.getName() + ": " + method.getReturnType().getCanonicalName();
            })
            .filter(this::isNotJacocoInit)
            .collect(Collectors.toList());

        Assertions.assertThat(methods).containsOnly(
            "pojoConfigValue: ru.tinkoff.kora.config.annotation.processor.cases.PojoConfig",
            "@RecordConfig recConfigValue: ru.tinkoff.kora.config.annotation.processor.cases.RecordConfig",
            "recordConfigRoot: ru.tinkoff.kora.config.annotation.processor.cases.RecordConfigRoot"
        );
    }

    private Class<?> createModule(Class<?> targetClass) throws Exception {
        try {
            var classLoader = TestUtils.annotationProcess(targetClass, new ConfigRootAnnotationProcessor());
            return classLoader.loadClass(targetClass.getName() + "Module");
        } catch (Exception e) {
            if (e.getCause() != null) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    private String tagsPrefix(Method method) {
        var tag = method.getAnnotation(Tag.class);
        if(tag == null) {
            return "";
        }

        return Arrays.stream(tag.value())
            .map(Class::getSimpleName)
            .collect(Collectors.joining(",", "@", " "));
    }

    // will be added by jacoco agent if build runs with coverage report generation
    private boolean isNotJacocoInit(String name) {
        return !"$jacocoInit: boolean[]".equals(name);
    }
}
