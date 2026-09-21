package io.koraframework.validation.annotation.processor;

import io.koraframework.annotation.processor.common.TestUtils.CompileResultHolder;
import org.junit.jupiter.api.Assertions;
import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.application.graph.TypeRef;
import io.koraframework.validation.annotation.processor.testdata.ValidBar;
import io.koraframework.validation.annotation.processor.testdata.ValidFoo;
import io.koraframework.validation.annotation.processor.testdata.ValidOneOf;
import io.koraframework.validation.annotation.processor.testdata.ValidTaz;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.ValidatorModule;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ExtendWith(ValidRunner.ResourceRegisterExtension.class)
public abstract class ValidRunner extends Assertions implements ValidatorModule {

    private static final Namespace NAMESPACE = Namespace.create(ValidateRunner.class);
    private static volatile CompileResultHolder compileResultHolder = null;

    public static class ResourceRegisterExtension implements BeforeAllCallback {
        @Override
        public void beforeAll(ExtensionContext context) {
            context.getRoot().getStore(NAMESPACE).computeIfAbsent(
                "sharedCompileResourceValidRunner",
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

    protected Validator<ValidFoo> getFooValidator() {
        Class<?> clazz = getClazz("io.koraframework.validation.annotation.processor.testdata.$ValidFoo_Validator");
        return getClazzInstance(clazz,
            notEmptyStringValidatorFactory(),
            patternStringValidatorFactory(),
            rangeLongValidatorFactory(),
            getBarValidator()
        );
    }

    protected Validator<ValidBar> getBarValidator() {
        Class<?> clazz = getClazz("io.koraframework.validation.annotation.processor.testdata.$ValidBar_Validator");
        return getClazzInstance(clazz,
            notBlankStringValidatorFactory(),
            sizeStringValidatorFactory(),
            sizeListValidatorFactory(TypeRef.of(Integer.class)),
            listValidator(getTazValidator(), TypeRef.of(ValidTaz.class))
        );
    }

    protected Validator<ValidTaz> getTazValidator() {
        Class<?> clazz = getClazz("io.koraframework.validation.annotation.processor.testdata.$ValidTaz_Validator");
        return getClazzInstance(clazz,
            patternStringValidatorFactory()
        );
    }

    protected Validator<ValidOneOf> getOneOfValidator() {
        Class<?> clazz = getClazz("io.koraframework.validation.annotation.processor.testdata.$ValidOneOf_Validator");
        return getClazzInstance(clazz,
            oneOfStringValidatorFactory()
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
            if (compileResultHolder == null) {
                synchronized (ValidateRunner.class) {
                    if (compileResultHolder == null) {
                        final List<Class<?>> classes = List.of(ValidFoo.class, ValidBar.class, ValidTaz.class, ValidOneOf.class);
                        compileResultHolder = TestUtils.annotationProcess(classes, new ValidAnnotationProcessor());
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
