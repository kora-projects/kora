package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import java.math.BigInteger
import java.util.*

object KnownType {
    private val bigInteger = BigInteger::class.asClassName()
    private val uuid = UUID::class.asClassName()
    private val binary = ByteArray::class.asClassName()

    fun detect(type: KSType): KnownTypesEnum? {
        val classDecl = type.declaration
        if (classDecl !is KSClassDeclaration) {
            return null
        }
        val className = classDecl.toClassName()
        return when (className) {
            STRING -> KnownTypesEnum.STRING
            INT -> KnownTypesEnum.INTEGER
            LONG -> KnownTypesEnum.LONG
            DOUBLE -> KnownTypesEnum.DOUBLE
            FLOAT -> KnownTypesEnum.FLOAT
            SHORT -> KnownTypesEnum.SHORT
            bigInteger -> KnownTypesEnum.BIG_INTEGER
            BOOLEAN -> KnownTypesEnum.BOOLEAN
            binary -> KnownTypesEnum.BINARY
            uuid -> KnownTypesEnum.UUID
            else -> null
        }
    }

    enum class KnownTypesEnum {
        STRING,
        BOOLEAN,
        INTEGER,
        BIG_INTEGER,
        DOUBLE,
        FLOAT,
        LONG,
        SHORT,
        BINARY,
        UUID
    }
}
