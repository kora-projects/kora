package ru.tinkoff.kora.test.extension.junit5;

import jakarta.annotation.Nonnull;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class KoraGraphModification {

    private final List<GraphModification> modifications = new ArrayList<>();

    private KoraGraphModification() {
    }

    public static KoraGraphModification create() {
        return new KoraGraphModification();
    }

    /**
     * Component that should be added to Graph Context
     */
    @Nonnull
    public <T> KoraGraphModification addComponent(@Nonnull Type typeToAdd,
                                                  @Nonnull Supplier<T> instanceSupplier) {
        modifications.add(new GraphAddition(s -> instanceSupplier.get(), new GraphCandidate(typeToAdd)));
        return this;
    }

    /**
     * Component that should be added to Graph Context
     */
    @Nonnull
    public <T> KoraGraphModification addComponent(@Nonnull Type typeToAdd,
                                                  @Nonnull List<Class<?>> tags,
                                                  @Nonnull Supplier<T> instanceSupplier) {
        if (tags.isEmpty()) {
            return addComponent(typeToAdd, instanceSupplier);
        } else {
            modifications.add(new GraphAddition(s -> instanceSupplier.get(), new GraphCandidate(typeToAdd, tags)));
            return this;
        }
    }

    /**
     * Component that should be added to Graph Context
     */
    @Nonnull
    public <T> KoraGraphModification addComponent(@Nonnull Type typeToAdd,
                                                  @Nonnull Function<KoraAppGraph, T> instanceSupplier) {
        modifications.add(new GraphAddition(instanceSupplier, new GraphCandidate(typeToAdd)));
        return this;
    }

    /**
     * Component that should be added to Graph Context
     */
    @Nonnull
    public <T> KoraGraphModification addComponent(@Nonnull Type typeToAdd,
                                                  @Nonnull List<Class<?>> tags,
                                                  @Nonnull Function<KoraAppGraph, T> instanceSupplier) {
        if (tags.isEmpty()) {
            return addComponent(typeToAdd, instanceSupplier);
        } else {
            modifications.add(new GraphAddition(instanceSupplier, new GraphCandidate(typeToAdd, tags)));
            return this;
        }
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph
     */
    @Nonnull
    public <T> KoraGraphModification replaceComponent(@Nonnull Type typeToReplace,
                                                      @Nonnull Supplier<? extends T> replacementSupplier) {
        modifications.add(new GraphReplacementWithDeps<T>(g -> replacementSupplier.get(), new GraphCandidate(typeToReplace)));
        return this;
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph
     */
    @Nonnull
    public <T> KoraGraphModification replaceComponent(@Nonnull Type typeToReplace,
                                                      @Nonnull List<Class<?>> tags,
                                                      @Nonnull Supplier<? extends T> replacementSupplier) {
        if (tags.isEmpty()) {
            return replaceComponent(typeToReplace, replacementSupplier);
        } else {
            modifications.add(new GraphReplacementWithDeps<T>(g -> replacementSupplier.get(), new GraphCandidate(typeToReplace, tags)));
            return this;
        }
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph
     */
    @Nonnull
    public <T> KoraGraphModification replaceComponent(@Nonnull Type typeToReplace,
                                                      @Nonnull Function<KoraAppGraph, ? extends T> replacementSupplier) {
        modifications.add(new GraphReplacementWithDeps<T>(replacementSupplier, new GraphCandidate(typeToReplace)));
        return this;
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph
     */
    @Nonnull
    public <T> KoraGraphModification replaceComponent(@Nonnull Type typeToReplace,
                                                      @Nonnull List<Class<?>> tags,
                                                      @Nonnull Function<KoraAppGraph, ? extends T> replacementSupplier) {
        if (tags.isEmpty()) {
            return replaceComponent(typeToReplace, replacementSupplier);
        } else {
            modifications.add(new GraphReplacementWithDeps<>(replacementSupplier, new GraphCandidate(typeToReplace, tags)));
            return this;
        }
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph, original component is also available
     */
    @Nonnull
    public <T> KoraGraphModification proxyComponent(@Nonnull Type typeToReplace,
                                                    @Nonnull Function<T, ? extends T> proxyFunction) {
        modifications.add(new GraphProxy<T>((original, graph) -> proxyFunction.apply(original), new GraphCandidate(typeToReplace)));
        return this;
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph, original component is also available
     */
    @Nonnull
    public <T> KoraGraphModification proxyComponent(@Nonnull Type typeToReplace,
                                                    @Nonnull List<Class<?>> tags,
                                                    @Nonnull Function<T, ? extends T> proxyFunction) {
        if (tags.isEmpty()) {
            return proxyComponent(typeToReplace, proxyFunction);
        } else {
            modifications.add(new GraphProxy<T>((original, graph) -> proxyFunction.apply(original), new GraphCandidate(typeToReplace, tags)));
            return this;
        }
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph, original component is also available
     */
    @Nonnull
    public <T> KoraGraphModification proxyComponent(@Nonnull Type typeToReplace,
                                                    @Nonnull BiFunction<T, KoraAppGraph, ? extends T> proxyFunction) {
        modifications.add(new GraphProxy<T>(proxyFunction, new GraphCandidate(typeToReplace)));
        return this;
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph, original component is also available
     */
    @Nonnull
    public <T> KoraGraphModification proxyComponent(@Nonnull Type typeToReplace,
                                                    @Nonnull List<Class<?>> tags,
                                                    @Nonnull BiFunction<T, KoraAppGraph, ? extends T> proxyFunction) {
        if (tags.isEmpty()) {
            return proxyComponent(typeToReplace, proxyFunction);
        } else {
            modifications.add(new GraphProxy<T>(proxyFunction, new GraphCandidate(typeToReplace, tags)));
            return this;
        }
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph, original component is also available
     */
    @Nonnull
    public <T> KoraGraphModification proxyComponent(@Nonnull Class<T> typeToReplace,
                                                    @Nonnull Function<T, ? extends T> proxyFunction) {
        return proxyComponent(((Type) typeToReplace), proxyFunction);
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph, original component is also available
     */
    @Nonnull
    public <T> KoraGraphModification proxyComponent(@Nonnull Class<T> typeToReplace,
                                                    @Nonnull List<Class<?>> tags,
                                                    @Nonnull Function<T, ? extends T> proxyFunction) {
        return proxyComponent(((Type) typeToReplace), tags, proxyFunction);
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph, original component is also available
     */
    @Nonnull
    public <T> KoraGraphModification proxyComponent(@Nonnull Class<T> typeToReplace,
                                                    @Nonnull BiFunction<T, KoraAppGraph, ? extends T> proxyFunction) {
        return proxyComponent(((Type) typeToReplace), proxyFunction);
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph, original component is also available
     */
    @Nonnull
    public <T> KoraGraphModification proxyComponent(@Nonnull Class<T> typeToReplace,
                                                    @Nonnull List<Class<?>> tags,
                                                    @Nonnull BiFunction<T, KoraAppGraph, ? extends T> proxyFunction) {
        return proxyComponent(((Type) typeToReplace), tags, proxyFunction);
    }

    /**
     * Component that should replace existing one with Mock AND all real component dependencies removed for graph
     */
    @Nonnull
    public <T> KoraGraphModification mockComponent(@Nonnull Type typeToMock,
                                                   @Nonnull Supplier<? extends T> replacementSupplier) {
        modifications.add(new GraphReplacementNoDeps<T>(g -> replacementSupplier.get(), new GraphCandidate(typeToMock)));
        return this;
    }

    /**
     * Component that should replace existing one with Mock AND all real component dependencies removed for graph
     */
    @Nonnull
    public <T> KoraGraphModification mockComponent(@Nonnull Type typeToMock,
                                                   @Nonnull List<Class<?>> tags,
                                                   @Nonnull Supplier<? extends T> replacementSupplier) {
        if (tags.isEmpty()) {
            return mockComponent(typeToMock, replacementSupplier);
        } else {
            modifications.add(new GraphReplacementNoDeps<T>(g -> replacementSupplier.get(), new GraphCandidate(typeToMock, tags)));
            return this;
        }
    }

    @Nonnull
    List<GraphModification> getModifications() {
        return modifications;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KoraGraphModification that)) return false;
        return Objects.equals(modifications, that.modifications);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modifications);
    }
}
