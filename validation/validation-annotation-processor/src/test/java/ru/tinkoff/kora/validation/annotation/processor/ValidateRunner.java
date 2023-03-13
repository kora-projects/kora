package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.validation.annotation.processor.testdata.*;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.constraint.ValidationModule;

import java.util.List;

public abstract class ValidateRunner extends Assertions implements ValidationModule {

    private static ClassLoader classLoader = null;

    protected ValidateSync getValidateSync() {
        try {
            final ClassLoader classLoader = getClassLoader();
            final Class<?> clazz = classLoader.loadClass("ru.tinkoff.kora.validation.annotation.processor.testdata.$ValidateSync__AopProxy");
            return (ValidateSync) clazz.getConstructors()[0].newInstance(
                rangeIntegerConstraintFactory(),
                notEmptyStringConstraintFactory(),
                getTazValidator(),
                sizeListConstraintFactory(TypeRef.of(ValidTaz.class)),
                listValidator(getTazValidator(), TypeRef.of(ValidTaz.class))
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected ValidateMono getValidateMono() {
        try {
            final ClassLoader classLoader = getClassLoader();
            final Class<?> clazz = classLoader.loadClass("ru.tinkoff.kora.validation.annotation.processor.testdata.$ValidateMono__AopProxy");
            return (ValidateMono) clazz.getConstructors()[0].newInstance(
                rangeIntegerConstraintFactory(),
                notEmptyStringConstraintFactory(),
                getTazValidator(),
                sizeListConstraintFactory(TypeRef.of(ValidTaz.class)),
                listValidator(getTazValidator(), TypeRef.of(ValidTaz.class))
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected ValidateFlux getValidateFlux() {
        try {
            final ClassLoader classLoader = getClassLoader();
            final Class<?> clazz = classLoader.loadClass("ru.tinkoff.kora.validation.annotation.processor.testdata.$ValidateFlux__AopProxy");
            return (ValidateFlux) clazz.getConstructors()[0].newInstance(
                rangeIntegerConstraintFactory(),
                notEmptyStringConstraintFactory(),
                getTazValidator(),
                sizeListConstraintFactory(TypeRef.of(ValidTaz.class)),
                listValidator(getTazValidator(), TypeRef.of(ValidTaz.class))
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Validator<ValidTaz> getTazValidator() {
        try {
            final ClassLoader classLoader = getClassLoader();
            final Class<?> clazz = classLoader.loadClass("ru.tinkoff.kora.validation.annotation.processor.testdata.$ValidTaz_Validator");
            return (Validator<ValidTaz>) clazz.getConstructors()[0].newInstance(patternStringConstraintFactory());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ClassLoader getClassLoader() {
        try {
            if (classLoader == null) {
                final List<Class<?>> classes = List.of(ValidTaz.class, ValidateFlux.class, ValidateMono.class, ValidateSync.class);
                classLoader = TestUtils.annotationProcess(classes, new ValidAnnotationProcessor(), new AopAnnotationProcessor());
            }

            return classLoader;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
