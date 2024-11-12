package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.validation.annotation.processor.testdata.ValidBar;
import ru.tinkoff.kora.validation.annotation.processor.testdata.ValidFoo;
import ru.tinkoff.kora.validation.annotation.processor.testdata.ValidTaz;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ValidRunner extends Assertions implements ValidatorModule {

    private static ClassLoader classLoader = null;

    protected Validator<ValidFoo> getFooValidator() {
        Class<?> clazz = getClazz("ru.tinkoff.kora.validation.annotation.processor.testdata.$ValidFoo_Validator");
        return getClazzInstance(clazz,
            notEmptyStringConstraintFactory(),
            patternStringConstraintFactory(),
            rangeLongConstraintFactory(),
            getBarValidator()
        );
    }

    protected Validator<ValidBar> getBarValidator() {
        Class<?> clazz = getClazz("ru.tinkoff.kora.validation.annotation.processor.testdata.$ValidBar_Validator");
        return getClazzInstance(clazz,
            notBlankStringConstraintFactory(),
            sizeStringConstraintFactory(),
            sizeListConstraintFactory(TypeRef.of(Integer.class)),
            listValidator(getTazValidator(), TypeRef.of(ValidTaz.class))
        );
    }

    protected Validator<ValidTaz> getTazValidator() {
        Class<?> clazz = getClazz("ru.tinkoff.kora.validation.annotation.processor.testdata.$ValidTaz_Validator");
        return getClazzInstance(clazz,
            patternStringConstraintFactory()
        );
    }

    protected <T> T getClazzInstance(Class<?> clazz, Object... params) {
        try {
            Constructor<?> constructor = clazz.getConstructors()[0];
            return (T) constructor.newInstance(params);
        } catch (IllegalArgumentException e) {
            final String paramsExpected = Arrays.stream(clazz.getConstructors()[0].getParameters())
                .map(p -> p.getParameterizedType().toString())
                .collect(Collectors.joining(", ", "[", "]"));

            final String paramsActual = Arrays.stream(params)
                .map(p -> p.getClass().getGenericInterfaces()[0].toString())
                .collect(Collectors.joining(", ", "[", "]"));

            throw new IllegalStateException("Class '" + clazz.getCanonicalName() + "' constructor params\nExpected: " + paramsExpected + "\nActual: " + paramsActual);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Class<?> getClazz(String clazzName) {
        try {
            final ClassLoader classLoader = getClassLoader();
            return classLoader.loadClass(clazzName);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ClassLoader getClassLoader() {
        try {
            if (classLoader == null) {
                final List<Class<?>> classes = List.of(ValidFoo.class, ValidBar.class, ValidTaz.class);
                classLoader = TestUtils.annotationProcess(classes, new ValidAnnotationProcessor());
            }

            return classLoader;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
