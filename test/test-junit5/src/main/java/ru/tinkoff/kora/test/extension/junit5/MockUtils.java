package ru.tinkoff.kora.test.extension.junit5;

import io.mockk.MockKKt;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;

final class MockUtils {

    private static final boolean isMockitoAvailable = isClassPresent("org.mockito.internal.util.MockUtil");
    private static final boolean isMockkAvailable = isClassPresent("io.mockk.MockKKt");

    private MockUtils() { }

    private static boolean isClassPresent(String className) {
        try {
            return KoraJUnit5Extension.class.getClassLoader().loadClass(className) != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static boolean isMockitoAvailable() {
        return isMockitoAvailable;
    }

    static boolean isMockkAvailable() {
        return isMockkAvailable;
    }

    static boolean haveKotlinReflect() {
        return isClassPresent("kotlin.reflect.jvm.ReflectJvmMapping");
    }

    static boolean haveAnyMockEngine() {
        return isMockitoAvailable || isMockkAvailable;
    }

    static void resetIfMock(Object mockCandidate) {
        if (isMockitoAvailable) {
            if (MockUtil.isMock(mockCandidate) || MockUtil.isSpy(mockCandidate)) {
                Mockito.reset(mockCandidate);
            }
        }

        if (isMockkAvailable) {
            if (MockKKt.isMockKMock(mockCandidate, true, true, true, false, false)) {
                MockKKt.clearMocks(mockCandidate, new Object[]{}, true, true, true, true, true);
            }
        }
    }

    static <T extends Annotation> T getAnnotation(AnnotatedElement element, Class<T> annotationClass) {
        var annotation = element.getAnnotation(annotationClass);

        if (annotation != null) {
            return annotation;
        }

        if (MockUtils.haveKotlinReflect()) {
            if (element instanceof Field field) {
                var prop = ReflectJvmMapping.getKotlinProperty(field);
                if (prop == null) {
                    return null;
                }

                return prop.getAnnotations()
                    .stream()
                    .filter(a -> a.annotationType().equals(annotationClass))
                    .findFirst()
                    .map(annotationClass::cast)
                    .orElse(null);
            }
        }

        return null;
    }
}
