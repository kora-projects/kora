package io.koraframework.database.symbol.processor.mongo

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec

/**
 * Codec registries a repository needs to hand the driver a typed `MongoCollection`. One registry is built per codec,
 * in the constructor, so nothing is allocated per call.
 */
class MongoCodecRegistries(
    private val type: TypeSpec.Builder,
    private val constructor: FunSpec.Builder
) {

    private val byCodecField = HashMap<String, String>()
    private var counter = 0

    fun forCodec(codecField: String): String = this.byCodecField.getOrPut(codecField) {
        val name = "_registry_${++this.counter}"
        this.type.addProperty(name, MongoTypes.codecRegistry, KModifier.PRIVATE)
        this.constructor.addStatement(
            "this.%N = %T.fromRegistries(%T.fromCodecs(this.%N), %T.getDefaultCodecRegistry())",
            name, MongoTypes.codecRegistries, MongoTypes.codecRegistries, codecField, MongoTypes.mongoClientSettings
        )
        name
    }
}
