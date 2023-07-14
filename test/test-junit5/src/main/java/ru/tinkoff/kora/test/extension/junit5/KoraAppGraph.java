package ru.tinkoff.kora.test.extension.junit5;

import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.common.Tag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
     * Try to find implementation in Graph by type and tags
     *
     * @param type of component to search
     * @param tags associated with component
     * @return type instance from Graph
     */
    @Nullable
    Object getFirst(@Nonnull Type type, Class<?>... tags);

    @Nullable
    <T> T getFirst(@Nonnull Class<T> type);

    /**
     * Try to find implementation in Graph by type and tags
     *
     * @param type of component to search
     * @param tags associated with component
     * @param <T>  type parameter
     * @return type instance from Graph
     */
    @Nullable
    <T> T getFirst(@Nonnull Class<T> type, Class<?>... tags);

    @Nonnull
    default Optional<Object> findFirst(@Nonnull Type type) {
        return Optional.ofNullable(getFirst(type));
    }

    /**
     * Try to find implementation in Graph by type and tags
     *
     * @param type of component to search
     * @param tags associated with component
     * @return type instance from Graph
     */
    @Nonnull
    default Optional<Object> findFirst(@Nonnull Type type, Class<?>... tags) {
        return Optional.ofNullable(getFirst(type, tags));
    }

    @Nonnull
    default <T> Optional<T> findFirst(@Nonnull Class<T> type) {
        return Optional.ofNullable(getFirst(type));
    }

    /**
     * Try to find implementation in Graph by type and tags
     *
     * @param type of component to search
     * @param tags associated with component
     * @param <T>  type parameter
     * @return type instance from Graph
     */
    @Nonnull
    default <T> Optional<T> findFirst(@Nonnull Class<T> type, Class<?>... tags) {
        return Optional.ofNullable(getFirst(type, tags));
    }

    @Nonnull
    List<Object> getAll(@Nonnull Type type);

    /**
     * Try to find implementation in Graph by type using {@link Tag.Any}
     *
     * @param type of component to search
     * @param tags associated with component
     * @return component instance from Graph
     */
    @Nonnull
    List<Object> getAll(@Nonnull Type type, Class<?>... tags);

    @Nonnull
    <T> List<T> getAll(@Nonnull Class<T> type);

    /**
     * Try to find implementation in Graph by type using {@link Tag.Any}
     *
     * @param type of component to search
     * @param tags associated with component
     * @param <T>  type parameter
     * @return type instance from Graph
     */
    @Nonnull
    <T> List<T> getAll(@Nonnull Class<T> type, Class<?>... tags);
}
