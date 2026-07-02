package io.koraframework.config.common.mapper;

import io.koraframework.common.annotation.Mapping;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * <b>Русский</b>: Контракт для извлечения классов отображения конфигурации в рамках внешних библиотек.
 * Впоследствии интерфейс может быть использован для внедрения как зависимость.
 * <hr>
 * <b>English</b>: Contract to extract configuration mapping classes within external libraries.
 * Subsequently, the interface can be used for deployment as a dependency.
 *
 * @see ConfigMapper
 */
@FunctionalInterface
public interface ConfigValueMapper<T> extends Mapping.MappingFunction {

    @Nullable
    T map(ConfigValue<?> value);

    default T mapOrThrow(ConfigValue<?> value) {
        var config = map(value);
        if (config == null) {
            throw ConfigValueException.missingValueAfterParse(value);
        }
        return config;
    }

    default <U> ConfigValueMapper<U> andThen(Function<@Nullable T, U> function) {
        return value -> function.apply(this.map(value));
    }
}
