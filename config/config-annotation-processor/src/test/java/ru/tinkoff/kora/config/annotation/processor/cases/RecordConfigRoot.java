package ru.tinkoff.kora.config.annotation.processor.cases;

import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.ConfigRoot;

@ConfigRoot
public record RecordConfigRoot(
    PojoConfig pojo,
    @Tag(RecordConfig.class) RecordConfig rec
) {
}
