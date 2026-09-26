package io.koraframework.validation.annotation.processor;

import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.annotation.processor.common.TestUtils.CompileResultHolder;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.application.graph.TypeRef;
import io.koraframework.validation.annotation.processor.testdata.ValidTaz;
import io.koraframework.validation.annotation.processor.testdata.ValidateCompletionStage;
import io.koraframework.validation.annotation.processor.testdata.ValidateSync;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.ValidatorModule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

import java.util.List;

@ExtendWith(ValidateRunner.ResourceRegisterExtension.class)
public abstract class ValidateRunner extends Assertions implements ValidatorModule {

    private static final Namespace NAMESPACE = Namespace.create(ValidateRunner.class);
    private static volatile CompileResultHolder compileResultHolder = null;

    public static class ResourceRegisterExtension implements BeforeAllCallback {
        @Override
        public void beforeAll(ExtensionContext context) {
            context.getRoot().getStore(NAMESPACE).computeIfAbsent(
                "sharedCompileResourceValidateRunner",
                _ -> new SharedCompileResource(),
                SharedCompileResource.class
            );
        }
    }

    private static class SharedCompileResource implements AutoCloseable {
        @Override
        public void close() throws Exception {
            synchronized (ValidateRunner.class) {
                if (compileResultHolder != null) {
                    compileResultHolder.close();
                    compileResultHolder = null;
                }
            }
        }
    }

    protected ValidateSync getValidateSync() {
        final ClassLoader classLoader = getClassLoader();
        return getValidateSync(classLoader);
    }

    protected ValidateSync getValidateSync(ClassLoader classLoader) {
        try {
            final Class<?> clazz = classLoader.loadClass("io.koraframework.validation.annotation.processor.testdata.$ValidateSync__AopProxy");
            return (ValidateSync) clazz.getConstructors()[0].newInstance(
                rangeIntegerValidatorFactory(),
                notEmptyStringValidatorFactory(),
                patternStringValidatorFactory(),
                getTazValidator(classLoader),
                sizeListValidatorFactory(TypeRef.of(ValidTaz.class)),
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
                rangeIntegerValidatorFactory(),
                notEmptyStringValidatorFactory(),
                patternStringValidatorFactory(),
                getTazValidator(classLoader),
                sizeListValidatorFactory(TypeRef.of(ValidTaz.class)),
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
            return (Validator<ValidTaz>) clazz.getConstructors()[0].newInstance(patternStringValidatorFactory());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ClassLoader getClassLoader() {
        try {
            if (compileResultHolder == null) {
                synchronized (ValidateRunner.class) {
                    if (compileResultHolder == null) {
                        final List<Class<?>> classes = List.of(ValidTaz.class, ValidateCompletionStage.class, ValidateSync.class);
                        compileResultHolder = TestUtils.annotationProcess(classes, new ValidAnnotationProcessor(), new AopAnnotationProcessor());
                    }
                }
            }
            return compileResultHolder.classLoader();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
