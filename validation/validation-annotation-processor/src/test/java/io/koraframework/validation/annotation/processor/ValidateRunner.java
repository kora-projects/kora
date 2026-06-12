package io.koraframework.validation.annotation.processor;

import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.application.graph.TypeRef;
import io.koraframework.validation.annotation.processor.testdata.ValidTaz;
import io.koraframework.validation.annotation.processor.testdata.ValidateCompletionStage;
import io.koraframework.validation.annotation.processor.testdata.ValidateSync;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.ValidatorModule;
import org.junit.jupiter.api.Assertions;

import java.util.List;

public abstract class ValidateRunner extends Assertions implements ValidatorModule {

    private static ClassLoader classLoader = null;

    protected ValidateSync getValidateSync() {
        final ClassLoader classLoader = getClassLoader();
        return getValidateSync(classLoader);
    }

    protected ValidateSync getValidateSync(ClassLoader classLoader) {
        try {
            final Class<?> clazz = classLoader.loadClass("io.koraframework.validation.annotation.processor.testdata.$ValidateSync__AopProxy");
            return (ValidateSync) clazz.getConstructors()[0].newInstance(
                rangeIntegerConstraintFactory(),
                notEmptyStringConstraintFactory(),
                patternStringConstraintFactory(),
                getTazValidator(classLoader),
                sizeListConstraintFactory(TypeRef.of(ValidTaz.class)),
                listValidator(getTazValidator(classLoader), TypeRef.of(ValidTaz.class))
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected ValidateCompletionStage getValidateCompletionStage() {
        final ClassLoader classLoader = getClassLoader();
        return getValidateCompletionStage(classLoader);
    }

    protected ValidateCompletionStage getValidateCompletionStage(ClassLoader classLoader) {
        try {
            final Class<?> clazz = classLoader.loadClass("io.koraframework.validation.annotation.processor.testdata.$ValidateCompletionStage__AopProxy");
            return (ValidateCompletionStage) clazz.getConstructors()[0].newInstance(
                rangeIntegerConstraintFactory(),
                notEmptyStringConstraintFactory(),
                patternStringConstraintFactory(),
                getTazValidator(classLoader),
                sizeListConstraintFactory(TypeRef.of(ValidTaz.class)),
                listValidator(getTazValidator(classLoader), TypeRef.of(ValidTaz.class))
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Validator<ValidTaz> getTazValidator() {
        final ClassLoader classLoader = getClassLoader();
        return getTazValidator(classLoader);
    }

    protected Validator<ValidTaz> getTazValidator(ClassLoader classLoader) {
        try {
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
                final List<Class<?>> classes = List.of(ValidTaz.class, ValidateCompletionStage.class, ValidateSync.class);
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
