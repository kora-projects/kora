package io.koraframework.annotation.processor.common;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;

/**
 * Types generated for the same source element are nested into a single top level holder type.
 * Every source file created by an annotation processor is checked by the compiler against all the files
 * created before it, so the number of generated files matters much more than their size.
 */
public final class GeneratedHolder {

    private GeneratedHolder() {}

    /**
     * @return name of the holder type generated for the element, e.g. <code>$Outer_Inner_Json</code>
     */
    public static ClassName name(Elements elements, Element element, String postfix) {
        var packageName = elements.getPackageOf(element).getQualifiedName().toString();
        return ClassName.get(packageName, NameUtils.generatedType(element, postfix));
    }

    /**
     * @return builder of the final class that can't be instantiated and only holds nested generated types
     */
    public static TypeSpec.Builder classBuilder(ClassName name, Class<?> generator) {
        return TypeSpec.classBuilder(name)
            .addAnnotation(AnnotationUtils.generated(generator))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
    }
}
