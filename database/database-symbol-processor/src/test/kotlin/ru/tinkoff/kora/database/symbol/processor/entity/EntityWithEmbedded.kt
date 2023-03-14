package ru.tinkoff.kora.database.symbol.processor.entity

import ru.tinkoff.kora.database.common.annotation.Embedded

data class EntityWithEmbedded(@Embedded val f1: EmbeddedEntity, @Embedded val f2: EmbeddedEntity?, @Embedded("field_3_") val f3: EmbeddedEntity?) {
    data class EmbeddedEntity(val f1: String, val f2: Int)
}
