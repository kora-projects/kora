package io.koraframework.database.common.annotation.processor;

import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.database.annotation.processor.RepositoryAnnotationProcessor;

import java.lang.reflect.Constructor;

public class DbTestUtils {

    public static <T> T compile(Class<T> repository, Object... params) {
        try {
            var cl = TestUtils.annotationProcess(repository, new RepositoryAnnotationProcessor());
            @SuppressWarnings("unchecked")
            var clazz = (Class<? extends T>) cl.loadClass(repository.getPackageName() + ".$" + repository.getSimpleName() + "_Impl");
            assert clazz.getConstructors().length == 1;
            var constructor = (Constructor<? extends T>) clazz.getConstructors()[0];
            return constructor.newInstance(params);
        } catch (RuntimeException throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
    public static <T> Class<? extends T> compileClass(Class<T> repository) {
        try {
            var cl = TestUtils.annotationProcess(repository, new RepositoryAnnotationProcessor());
            @SuppressWarnings("unchecked")
            var clazz = (Class<? extends T>) cl.loadClass(repository.getPackageName() + ".$" + repository.getSimpleName() + "_Impl");
            return clazz;
        } catch (RuntimeException throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

}
