package ru.tinkoff.kora.test.extension.junit5;

import org.jspecify.annotations.Nullable;
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
    Object getFirst(Type type);

    /**
     * Try to find implementation in Graph by type and tag
     *
     * @param type of component to search
     * @param tag  associated with component
     * @return type instance from Graph
     */
    @Nullable
    Object getFirst(Type type, @Nullable Class<?> tag);

    @Nullable
    <T> T getFirst(Class<T> type);

    /**
     * Try to find implementation in Graph by type and tag
     *
     * @param type of component to search
     * @param tag  associated with component
     * @param <T>  type parameter
     * @return type instance from Graph
     */
    @Nullable
    <T> T getFirst(Class<T> type, @Nullable Class<?> tag);

    default Optional<@Nullable Object> findFirst(Type type) {
        return Optional.ofNullable(getFirst(type));
    }

    /**
     * Try to find implementation in Graph by type and tag
     *
     * @param type of component to search
     * @param tag  associated with component
     * @return type instance from Graph
     */
    default Optional<@Nullable Object> findFirst(Type type, Class<?> tag) {
        return Optional.ofNullable(getFirst(type, tag));
    }

    default <T> Optional<@Nullable T> findFirst(Class<T> type) {
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
    default <T> Optional<T> findFirst(Class<T> type, @Nullable Class<?> tag) {
        return Optional.ofNullable(getFirst(type, tag));
    }

    List<Object> getAll(Type type);

    /**
     * Try to find implementation in Graph by type using {@link Tag.Any}
     *
     * @param type of component to search
     * @param tag  associated with component
     * @return component instance from Graph
     */
    List<Object> getAll(Type type, @Nullable Class<?> tag);

    <T> List<T> getAll(Class<T> type);

    /**
     * Try to find implementation in Graph by type using {@link Tag.Any}
     *
     * @param type of component to search
     * @param tag  associated with component
     * @param <T>  type parameter
     * @return type instance from Graph
     */
    <T> List<T> getAll(Class<T> type, @Nullable Class<?> tag);
}
