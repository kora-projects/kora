package ru.tinkoff.kora.annotation.processor.common.compile;


import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class KoraCompileTestJavaClassLoader extends ClassLoader {
    private final List<JavaFileObject> objects;
    private final HashMap<String, Class<?>> definedClasses = new HashMap<>();

    protected KoraCompileTestJavaClassLoader(List<JavaFileObject> objects) {
        super(Thread.currentThread().getContextClassLoader());
        this.objects = objects;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        var maybeDefined = this.definedClasses.get(name);
        if (maybeDefined != null) {
            return maybeDefined;
        }
        for (var object : this.objects) {
            if (object.getName().equals(name)) {
                try {
                    var bytes = object.openInputStream().readAllBytes();
                    var defined = this.defineClass(name, bytes, 0, bytes.length);
                    this.definedClasses.put(name, defined);
                    return defined;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        throw new ClassNotFoundException("Cannot find " + name);
    }
}
