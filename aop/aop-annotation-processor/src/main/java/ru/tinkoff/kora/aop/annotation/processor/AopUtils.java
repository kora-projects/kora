package ru.tinkoff.kora.aop.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class AopUtils {
    public static String aopProxyName(TypeElement typeElement) {
        return CommonUtils.getOuterClassesAsPrefix(typeElement) + typeElement.getSimpleName() + "__AopProxy";
    }

    @Nullable
    public static ExecutableElement findAopConstructor(TypeElement typeElement) {
        var publicConstructors = CommonUtils.findConstructors(typeElement, m -> m.contains(Modifier.PUBLIC));
        if (publicConstructors.size() == 1) {
            return publicConstructors.get(0);
        }
        if (publicConstructors.size() > 1) {
            return null;
        }
        var protectedConstructors = CommonUtils.findConstructors(typeElement, m -> m.contains(Modifier.PROTECTED));
        if (protectedConstructors.size() == 1) {
            return protectedConstructors.get(0);
        }
        if (protectedConstructors.size() > 1) {
            return null;
        }
        var packagePrivateConstructors = CommonUtils.findConstructors(typeElement, m -> !m.contains(Modifier.PRIVATE));
        if (packagePrivateConstructors.size() == 1) {
            return packagePrivateConstructors.get(0);
        }
        return null;
    }
}
