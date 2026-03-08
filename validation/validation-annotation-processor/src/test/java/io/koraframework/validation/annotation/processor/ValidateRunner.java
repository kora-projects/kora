package io.koraframework.validation.annotation.processor;

import org.junit.jupiter.api.Assertions;
import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.application.graph.TypeRef;
import io.koraframework.validation.annotation.processor.testdata.ValidTaz;
import io.koraframework.validation.annotation.processor.testdata.ValidateFuture;
import io.koraframework.validation.annotation.processor.testdata.ValidateSync;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.ValidatorModule;

import java.util.List;

public abstract class ValidateRunner extends Assertions implements ValidatorModule {

    private static ClassLoader classLoader = null;

    protected ValidateSync getValidateSync() {
        try {
            final ClassLoader classLoader = getClassLoader();
            final Class<?> clazz = classLoader.loadClass("io.koraframework.validation.annotation.processor.testdata.$ValidateSync__AopProxy");
            return (ValidateSync) clazz.getConstructors()[0].newInstance(
                rangeIntegerConstraintFactory(),
                notEmptyStringConstraintFactory(),
                patternStringConstraintFactory(),
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

    protected ValidateFuture getValidateFuture() {
        try {
            final ClassLoader classLoader = getClassLoader();
            final Class<?> clazz = classLoader.loadClass("io.koraframework.validation.annotation.processor.testdata.$ValidateFuture__AopProxy");
            return (ValidateFuture) clazz.getConstructors()[0].newInstance(
                rangeIntegerConstraintFactory(),
                notEmptyStringConstraintFactory(),
                patternStringConstraintFactory(),
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
            final Class<?> clazz = classLoader.loadClass("io.koraframework.validation.annotation.processor.testdata.$ValidTaz_Validator");
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
                final List<Class<?>> classes = List.of(ValidTaz.class, ValidateFuture.class, ValidateSync.class);
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
