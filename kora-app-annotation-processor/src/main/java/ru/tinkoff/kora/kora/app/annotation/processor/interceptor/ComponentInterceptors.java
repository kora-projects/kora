package ru.tinkoff.kora.kora.app.annotation.processor.interceptor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.annotation.processor.common.TagUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.ProcessingContext;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ComponentInterceptors {
    private final ProcessingContext ctx;
    private final List<ComponentInterceptor> interceptors;

    private ComponentInterceptors(ProcessingContext ctx, List<ComponentInterceptor> interceptors) {
        this.ctx = ctx;
        this.interceptors = interceptors;
    }

    public static ComponentInterceptors parseInterceptors(ProcessingContext ctx, Collection<ResolvedComponent> components) {
        var interceptors = new ArrayList<ComponentInterceptor>();
        for (var component : components) {
            var factory = parseInterceptor(ctx, component);
            if (factory != null) {
                interceptors.add(factory);
            }
        }
        return new ComponentInterceptors(ctx, interceptors);
    }

    @Nullable
    private static ComponentInterceptor parseInterceptor(ProcessingContext ctx, ResolvedComponent component) {
        if (!ctx.serviceTypeHelper.isInterceptor(component.type())) {
            return null;
        }
        var interceptedType = ctx.serviceTypeHelper.getInterceptedType(component.type());
        return new ComponentInterceptor(component, interceptedType);
    }

    public List<ComponentInterceptor> interceptorsFor(ResolvedComponent component) {
        var type = component.type();
        return this.interceptors.stream()
            .filter(interceptor -> this.ctx.serviceTypeHelper.isInterceptable(interceptor.interceptType(), type))
            .filter(interceptor -> TagUtils.tagsMatch(interceptor.component().tag(), component.tag()))
            .toList();
    }
}
