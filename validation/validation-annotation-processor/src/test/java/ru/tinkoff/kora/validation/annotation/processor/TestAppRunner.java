package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;
import ru.tinkoff.kora.validation.annotation.processor.testdata.Bar;
import ru.tinkoff.kora.validation.annotation.processor.testdata.Foo;
import ru.tinkoff.kora.validation.annotation.processor.testdata.Taz;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.constraint.ValidationModule;

import java.util.List;

public abstract class TestAppRunner extends Assertions implements ValidationModule {

    private static ClassLoader classLoader = null;

    protected Validator<Foo> getFooValidator() {
        try {
            final ClassLoader classLoader = getClassLoader();
            final Class<?> clazz = classLoader.loadClass("ru.tinkoff.kora.validation.annotation.processor.testdata.$Foo_Validator");
            return (Validator<Foo>) clazz.getConstructors()[0].newInstance(notEmptyStringConstraintFactory(),
                patternStringConstraintFactory(),
                rangeLongConstraintFactory(),
                getBarValidator());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Validator<Bar> getBarValidator() {
        try {
            final ClassLoader classLoader = getClassLoader();
            final Class<?> clazz = classLoader.loadClass("ru.tinkoff.kora.validation.annotation.processor.testdata.$Bar_Validator");
            return (Validator<Bar>) clazz.getConstructors()[0].newInstance(
                sizeListConstraintFactory(TypeRef.of(Integer.class)),
                listValidator(getTazValidator(), TypeRef.of(Taz.class)));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Validator<Taz> getTazValidator() {
        try {
            final ClassLoader classLoader = getClassLoader();
            final Class<?> clazz = classLoader.loadClass("ru.tinkoff.kora.validation.annotation.processor.testdata.$Taz_Validator");
            return (Validator<Taz>) clazz.getConstructors()[0].newInstance(patternStringConstraintFactory());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ClassLoader getClassLoader() {
        try {
            if (classLoader == null) {
                final List<Class<?>> classes = List.of(Foo.class, Bar.class, Taz.class);
                classLoader = TestUtils.annotationProcess(classes, new ValidationAnnotationProcessor());
            }

            return classLoader;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
