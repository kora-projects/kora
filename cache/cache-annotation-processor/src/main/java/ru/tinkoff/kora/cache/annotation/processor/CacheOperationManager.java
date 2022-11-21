package ru.tinkoff.kora.cache.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.MethodUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.regex.Pattern;

public final class CacheOperationManager {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z][0-9a-zA-Z_]*");
    private static final Map<String, CacheSignature> CACHE_NAME_TO_CACHE_KEY = new HashMap<>();

    private record CacheSignature(CacheSignature.Type key, String value, List<String> parameterTypes, CacheMeta.Origin origin) {

        public record Type(String packageName, String simpleName) {}
    }

    static void reset() {
        CACHE_NAME_TO_CACHE_KEY.clear();
    }

    public static CacheOperation getCacheOperation(ExecutableElement method, ProcessingEnvironment env) {
        final CacheMeta meta = CacheMetaUtils.getCacheMeta(method);
        final CacheSignature signature = getCacheSignature(meta, method, env);

        return new CacheOperation(meta,
            new CacheOperation.Key(signature.key().packageName(), signature.key().simpleName()),
            new CacheOperation.Value(signature.value())
        );
    }

    private static CacheSignature getCacheSignature(CacheMeta meta, ExecutableElement method, ProcessingEnvironment env) {
        for (CacheMeta.Manager manager : meta.managers()) {
            if (!NAME_PATTERN.matcher(manager.name()).matches()) {
                throw new IllegalArgumentException("Cache name for " + meta.origin() + " doesn't match pattern: " + NAME_PATTERN);
            }
        }

        final List<String> parameterTypes = new ArrayList<>();
        if (meta.parameters().isEmpty()) {
            method.getParameters().forEach(p -> parameterTypes.add(p.asType().toString()));
        } else {
            for (String parameter : meta.parameters()) {
                method.getParameters().stream()
                    .filter(p -> p.getSimpleName().contentEquals(parameter))
                    .findFirst()
                    .ifPresent(p -> parameterTypes.add(p.asType().toString()));
            }
        }

        final TypeMirror returnType = MethodUtils.isMono(method, env)
            ? ((DeclaredType) method.getReturnType()).getTypeArguments().get(0)
            : method.getReturnType();

        final CacheSignature signature = meta.managers().stream()
            .map(CacheMeta.Manager::name)
            .map(CACHE_NAME_TO_CACHE_KEY::get)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseGet(() -> {
                final String cacheName = meta.managers().get(0).name();
                final PackageElement packageElement = (PackageElement) method.getEnclosingElement().getEnclosingElement();
                final String packageName = (packageElement.isUnnamed())
                    ? ""
                    : packageElement.getQualifiedName().toString();

                final String nonVoidReturnType = MethodUtils.isVoid(returnType)
                    ? null
                    : returnType.toString();

                final CacheSignature.Type key = new CacheSignature.Type(packageName, "$CacheKey__" + cacheName);
                final CacheSignature cacheSignature = new CacheSignature(key, nonVoidReturnType, parameterTypes, meta.origin());
                for (CacheMeta.Manager manager : meta.managers()) {
                    CACHE_NAME_TO_CACHE_KEY.put(manager.name(), cacheSignature);
                }

                return cacheSignature;
            });

        if (!meta.type().equals(CacheMeta.Type.EVICT_ALL) && !meta.type().equals(CacheMeta.Type.EVICT)) {
            if (!signature.parameterTypes().equals(parameterTypes)) {
                throw new IllegalStateException("Cache Key parameters from " + signature.origin() + " mismatch with " + meta.origin()
                    + ", expected " + signature.parameterTypes() + " but was " + parameterTypes);
            }

            // Replace evict (void) operations previously saved
            if (!MethodUtils.isVoid(returnType)) {
                final String returnAsStr = returnType.toString();
                if (signature.value != null && !returnAsStr.equals(signature.value)) {
                    throw new IllegalStateException("Cache Value type from " + signature.origin() + " mismatch with " + meta.origin()
                        + ", expected " + signature.value() + " but was " + returnType);
                } else if (signature.value == null) {
                    final CacheSignature nonEvictSignature = new CacheSignature(signature.key(), returnAsStr, signature.parameterTypes(), meta.origin());
                    for (CacheMeta.Manager manager : meta.managers()) {
                        CACHE_NAME_TO_CACHE_KEY.put(manager.name(), nonEvictSignature);
                    }
                }
            }
        }

        return signature;
    }
}
