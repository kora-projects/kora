package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.validation.annotation.processor.testdata.ValidBar;
import ru.tinkoff.kora.validation.annotation.processor.testdata.ValidFoo;
import ru.tinkoff.kora.validation.annotation.processor.testdata.ValidTaz;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.constraint.ValidationModule;

import java.util.List;

public abstract class ValidRunner extends Assertions implements ValidationModule {

    private static ClassLoader classLoader = null;

    protected Validator<ValidFoo> getFooValidator() {
        try {
            final ClassLoader classLoader = getClassLoader();
            final Class<?> clazz = classLoader.loadClass("ru.tinkoff.kora.validation.annotation.processor.testdata.$ValidFoo_Validator");
            return (Validator<ValidFoo>) clazz.getConstructors()[0].newInstance(notEmptyStringConstraintFactory(),
                patternStringConstraintFactory(),
                rangeLongConstraintFactory(),
                getBarValidator());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Validator<ValidBar> getBarValidator() {
        try {
            final ClassLoader classLoader = getClassLoader();
            final Class<?> clazz = classLoader.loadClass("ru.tinkoff.kora.validation.annotation.processor.testdata.$ValidBar_Validator");
            return (Validator<ValidBar>) clazz.getConstructors()[0].newInstance(
                sizeListConstraintFactory(TypeRef.of(Integer.class)),
                listValidator(getTazValidator(), TypeRef.of(ValidTaz.class)));
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
