package ru.tinkoff.kora.test.extension.junit5;

import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
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
     * Component that should replace existing one with new one
     */
    @Nonnull
    public <T> KoraGraphModification replaceComponent(@Nonnull Type typeToReplace,
                                                      @Nonnull Supplier<? extends T> replacementSupplier) {
        modifications.add(new GraphReplacement<T>(g -> replacementSupplier.get(), new GraphCandidate(typeToReplace)));
        return this;
    }

    /**
     * Component that should replace existing one with new one
     */
    @Nonnull
    public <T> KoraGraphModification replaceComponent(@Nonnull Type typeToReplace,
                                                      @Nonnull List<Class<?>> tags,
                                                      @Nonnull Supplier<? extends T> replacementSupplier) {
        if (tags.isEmpty()) {
            return replaceComponent(typeToReplace, replacementSupplier);
        } else {
            modifications.add(new GraphReplacement<T>(g -> replacementSupplier.get(), new GraphCandidate(typeToReplace, tags)));
            return this;
        }
    }

    /**
     * Component that should replace existing one with new one
     */
    @Nonnull
    public <T> KoraGraphModification replaceComponent(@Nonnull Type typeToReplace,
                                                      @Nonnull Function<KoraAppGraph, ? extends T> replacementSupplier) {
        modifications.add(new GraphReplacement<T>(replacementSupplier, new GraphCandidate(typeToReplace)));
        return this;
    }

    /**
     * Component that should replace existing one with new one
     */
    @Nonnull
    public <T> KoraGraphModification replaceComponent(@Nonnull Type typeToReplace,
                                                      @Nonnull List<Class<?>> tags,
                                                      @Nonnull Function<KoraAppGraph, ? extends T> replacementSupplier) {
        if (tags.isEmpty()) {
            return replaceComponent(typeToReplace, replacementSupplier);
        } else {
            modifications.add(new GraphReplacement(replacementSupplier, new GraphCandidate(typeToReplace, tags)));
            return this;
        }
    }

    /**
     * Component that should be Mocked with {@link org.mockito.Mockito}
     */
    @Nonnull
    public KoraGraphModification mockComponent(@Nonnull Type typeToMock) {
        modifications.add(new GraphMock(new GraphCandidate(typeToMock)));
        return this;
    }

    /**
     * Component that should be Mocked with {@link org.mockito.Mockito}
     */
    @Nonnull
    public KoraGraphModification mockComponent(@Nonnull Type typeToMock,
                                               @Nonnull List<Class<?>> tags) {
        if (tags.isEmpty()) {
            return mockComponent(typeToMock);
        } else {
            modifications.add(new GraphMock(new GraphCandidate(typeToMock, tags)));
            return this;
        }
    }

    /**
     * Component that should be Mocked with {@link org.mockito.Mockito}
     */
    @Nonnull
    KoraGraphModification mockComponent(@Nonnull Type typeToMock,
                                        @Nullable Class<?>[] tags) {
        if (tags == null) {
            return mockComponent(typeToMock);
        } else {
            modifications.add(new GraphMock(new GraphCandidate(typeToMock, tags)));
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
