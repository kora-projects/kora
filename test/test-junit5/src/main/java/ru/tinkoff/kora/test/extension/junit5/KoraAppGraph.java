package ru.tinkoff.kora.test.extension.junit5;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.common.Tag;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

/**
 * {@link ApplicationGraphDraw} abstraction for {@link KoraAppTest}
 */
public interface KoraAppGraph {

    @Nullable
    Object getFirst(@Nonnull Type type);

    /**
     * Try to find implementation in Graph by type and tag
     *
     * @param type of component to search
     * @param tag  associated with component
     * @return type instance from Graph
     */
    @Nullable
    Object getFirst(@Nonnull Type type, @Nullable Class<?> tag);

    @Nullable
    <T> T getFirst(@Nonnull Class<T> type);

    /**
     * Try to find implementation in Graph by type and tag
     *
     * @param type of component to search
     * @param tag  associated with component
     * @param <T>  type parameter
     * @return type instance from Graph
     */
    @Nullable
    <T> T getFirst(@Nonnull Class<T> type, @Nullable Class<?> tag);

    @Nonnull
    default Optional<Object> findFirst(@Nonnull Type type) {
        return Optional.ofNullable(getFirst(type));
    }

    /**
     * Try to find implementation in Graph by type and tag
     *
     * @param type of component to search
     * @param tag  associated with component
     * @return type instance from Graph
     */
    @Nonnull
    default Optional<Object> findFirst(@Nonnull Type type, Class<?> tag) {
        return Optional.ofNullable(getFirst(type, tag));
    }

    @Nonnull
    default <T> Optional<T> findFirst(@Nonnull Class<T> type) {
        return Optional.ofNullable(getFirst(type));
    }

    /**
     * Try to find implementation in Graph by type and tag
     *
     * @param type of component to search
     * @param tag  associated with component
     * @param <T>  type parameter
     * @return type instance from Graph
     */
    @Nonnull
    default <T> Optional<T> findFirst(@Nonnull Class<T> type, @Nullable Class<?> tag) {
        return Optional.ofNullable(getFirst(type, tag));
    }

    @Nonnull
    List<Object> getAll(@Nonnull Type type);

    /**
     * Try to find implementation in Graph by type using {@link Tag.Any}
     *
     * @param type of component to search
     * @param tag  associated with component
     * @return component instance from Graph
     */
    @Nonnull
    List<Object> getAll(@Nonnull Type type, @Nullable Class<?> tag);

    @Nonnull
    <T> List<T> getAll(@Nonnull Class<T> type);

    /**
     * Try to find implementation in Graph by type using {@link Tag.Any}
     *
     * @param type of component to search
     * @param tag  associated with component
     * @param <T>  type parameter
     * @return type instance from Graph
     */
    @Nonnull
    <T> List<T> getAll(@Nonnull Class<T> type, @Nullable Class<?> tag);
}
