package ru.tinkoff.kora.config.common.extractor;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.function.Function;

/**
 * <b>Русский</b>: Контракт для извлечения классов отображения конфигурации в рамках внешних библиотек.
 * Впоследствии интерфейс может быть использован для внедрения как зависимость.
 * <hr>
 * <b>English</b>: Contract to extract configuration mapping classes within external libraries.
 * Subsequently, the interface can be used for deployment as a dependency.
 *
 * @see ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
 */
@FunctionalInterface
public interface ConfigValueExtractor<T> extends Mapping.MappingFunction {

    @Nullable
    T extract(ConfigValue<?> value);

    default <U> ConfigValueExtractor<U> map(Function<T, U> f) {
        return new ConfigValueExtractorMapping<>(this, f);
    }
}
