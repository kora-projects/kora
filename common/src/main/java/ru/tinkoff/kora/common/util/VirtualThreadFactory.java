package ru.tinkoff.kora.common.util;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class VirtualThreadFactory {
    private final BiFunction<String, Runnable, Thread> threadBuilder;

    @SuppressWarnings("unchecked")
    public VirtualThreadFactory() throws Throwable {
        var lookup = MethodHandles.lookup();
        var ofVirtualClass = lookup.findClass("java.lang.Thread$Builder$OfVirtual");
        var threadBuilderClass = lookup.findClass("java.lang.Thread$Builder");
        var ofVirtualMh = lookup.findStatic(Thread.class, "ofVirtual", MethodType.methodType(ofVirtualClass));

        var ofVirtual = (Supplier<Object>) LambdaMetafactory.metafactory(
            lookup,
            "get",
            MethodType.methodType(Supplier.class),
            MethodType.methodType(Object.class),
            ofVirtualMh,
            ofVirtualMh.type()
        ).getTarget().invokeExact();
        var nameMh = lookup.findVirtual(ofVirtualClass, "name", MethodType.methodType(ofVirtualClass, String.class));
        var named = (BiFunction<Object, String, Object>) LambdaMetafactory.metafactory(
            lookup,
            "apply",
            MethodType.methodType(BiFunction.class),
            MethodType.methodType(Object.class, Object.class, Object.class),
            nameMh,
            nameMh.type()
        ).getTarget().invokeExact();
        var unstartedMh = lookup.findVirtual(threadBuilderClass, "unstarted", MethodType.methodType(Thread.class, Runnable.class));
        var unstarted = (BiFunction<Object, Runnable, Thread>) LambdaMetafactory.metafactory(
            lookup,
            "apply",
            MethodType.methodType(BiFunction.class),
            MethodType.methodType(Object.class, Object.class, Object.class),
            unstartedMh,
            unstartedMh.type()
        ).getTarget().invokeExact();
        this.threadBuilder = (name, runnable) -> {
            var b = ofVirtual.get();
            b = named.apply(b, name);
            return unstarted.apply(b, runnable);
        };
    }

    public Thread newThread(String name, Runnable runnable) {
        return threadBuilder.apply(name, runnable);
    }
}
