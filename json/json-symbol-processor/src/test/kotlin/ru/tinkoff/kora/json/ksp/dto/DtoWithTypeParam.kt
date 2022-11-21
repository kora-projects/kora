package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.annotation.Json
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorField

@Json
@JsonDiscriminatorField("@type")
sealed interface DtoWithTypeParam<A, B> {
    data class FirstTpe<A, B>(val a: A, val b: B?, val c: Int) : DtoWithTypeParam<A, B>

    data class SecondTpe<A>(val a: A) : DtoWithTypeParam<A, Any?>

    data class ThirdTpe<B>(val b: B) : DtoWithTypeParam<Any?, B>
}
