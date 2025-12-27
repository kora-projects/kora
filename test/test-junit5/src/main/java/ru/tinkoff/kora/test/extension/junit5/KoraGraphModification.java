package ru.tinkoff.kora.test.extension.junit5;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class KoraGraphModification {

    private final List<GraphModification> modifications = new ArrayList<>();

    private KoraGraphModification() {}

    public static KoraGraphModification create() {
        return new KoraGraphModification();
    }

    /**
     * Component that should be added to Graph Context
     */
    public <T> KoraGraphModification addComponent(Type typeToAdd,
                                                  Supplier<T> instanceSupplier) {
        modifications.add(new GraphAddition(s -> instanceSupplier.get(), new GraphCandidate(typeToAdd)));
        return this;
    }

    /**
     * Component that should be added to Graph Context
     */
    public <T> KoraGraphModification addComponent(Type typeToAdd,
                                                  @Nullable Class<?> tag,
                                                  Supplier<T> instanceSupplier) {
        if (tag == null) {
            return addComponent(typeToAdd, instanceSupplier);
        } else {
            modifications.add(new GraphAddition(s -> instanceSupplier.get(), new GraphCandidate(typeToAdd, tag)));
            return this;
        }
    }

    /**
     * Component that should be added to Graph Context
     */
    public <T> KoraGraphModification addComponent(Type typeToAdd,
                                                  Function<KoraAppGraph, T> instanceSupplier) {
        modifications.add(new GraphAddition(instanceSupplier, new GraphCandidate(typeToAdd)));
        return this;
    }

    /**
     * Component that should be added to Graph Context
     */
    public <T> KoraGraphModification addComponent(Type typeToAdd,
                                                  @Nullable Class<?> tag,
                                                  Function<KoraAppGraph, T> instanceSupplier) {
        if (tag == null) {
            return addComponent(typeToAdd, instanceSupplier);
        } else {
            modifications.add(new GraphAddition(instanceSupplier, new GraphCandidate(typeToAdd, tag)));
            return this;
        }
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph
     */
    public <T> KoraGraphModification replaceComponent(Type typeToReplace,
                                                      Supplier<? extends T> replacementSupplier) {
        modifications.add(new GraphReplacementNoDeps<>(g -> replacementSupplier.get(), new GraphCandidate(typeToReplace)));
        return this;
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph
     */
    public <T> KoraGraphModification replaceComponent(Type typeToReplace,
                                                      @Nullable Class<?> tag,
                                                      Supplier<? extends T> replacementSupplier) {
        if (tag == null) {
            return replaceComponent(typeToReplace, replacementSupplier);
        } else {
            modifications.add(new GraphReplacementNoDeps<T>(g -> replacementSupplier.get(), new GraphCandidate(typeToReplace, tag)));
            return this;
        }
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph
     */
    public <T> KoraGraphModification replaceComponent(Type typeToReplace,
                                                      Function<KoraAppGraph, ? extends T> replacementSupplier) {
        modifications.add(new GraphReplacementWithDeps<T>(replacementSupplier, new GraphCandidate(typeToReplace)));
        return this;
    }

    /**
     * Component that should replace existing one with new one AND keeps its dependencies in graph
     */
    public <T> KoraGraphModification replaceComponent(Type typeToReplace,
                                                      @Nullable Class<?> tag,
                                                      Function<KoraAppGraph, ? extends T> replacementSupplier) {
        if (tag == null) {
            return replaceComponent(typeToReplace, replacementSupplier);
        } else {
            modifications.add(new GraphReplacementWithDeps<>(replacementSupplier, new GraphCandidate(typeToReplace, tag)));
            return this;
        }
    }

    /**
     * Component that should replace existing one with Mock AND all real component dependencies removed for graph
     */
    public <T> KoraGraphModification mockComponent(Type typeToMock,
                                                   Supplier<? extends T> replacementSupplier) {
        modifications.add(new GraphReplacementNoDeps<T>(g -> replacementSupplier.get(), new GraphCandidate(typeToMock)));
        return this;
    }

    /**
     * Component that should replace existing one with Mock AND all real component dependencies removed for graph
     */
    public <T> KoraGraphModification mockComponent(Type typeToMock,
                                                   @Nullable Class<?> tag,
                                                   Supplier<? extends T> replacementSupplier) {
        if (tag == null) {
            return mockComponent(typeToMock, replacementSupplier);
        } else {
            modifications.add(new GraphReplacementNoDeps<T>(g -> replacementSupplier.get(), new GraphCandidate(typeToMock, tag)));
            return this;
        }
    }

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
