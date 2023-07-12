package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType

class KnownType(private val resolver: Resolver) {
    private val any = resolver.builtIns.anyType
    private val nullableAny = resolver.builtIns.anyType.makeNullable()
    private val string = resolver.builtIns.stringType
    private val nullableString = string.makeNullable()
    private val boolean = resolver.builtIns.booleanType
    private val nullableBoolean = boolean.makeNullable()
    private val integer = resolver.builtIns.intType
    private val nullableInteger = integer.makeNullable()
    private val bigInteger = resolver.getClassDeclarationByName("java.math.BigInteger")!!.asType(listOf())
    private val bigDecimal = resolver.getClassDeclarationByName("java.math.BigDecimal")!!.asType(listOf())
    private val double = resolver.builtIns.doubleType
    private val nullableDouble = double.makeNullable()
    private val float = resolver.builtIns.floatType
    private val nullableFloat = float.makeNullable()
    private val long = resolver.builtIns.longType
    private val nullableLong = long.makeNullable()
    private val short = resolver.builtIns.shortType
    private val nullableShort = short.makeNullable()
    private val uuid = resolver.getClassDeclarationByName("java.util.UUID")!!.asType(listOf())
    private val binary = resolver.getClassDeclarationByName("kotlin.ByteArray")!!.asType(listOf())

    fun detect(type: KSType): KnownTypesEnum? {
        return when (type) {
            string, nullableString -> KnownTypesEnum.STRING
            integer, nullableInteger -> KnownTypesEnum.INTEGER
            long, nullableLong -> KnownTypesEnum.LONG
            double, nullableDouble -> KnownTypesEnum.DOUBLE
            float, nullableFloat -> KnownTypesEnum.FLOAT
            short, nullableShort -> KnownTypesEnum.SHORT
            bigInteger, bigInteger.makeNullable() -> KnownTypesEnum.BIG_INTEGER
            bigDecimal, bigDecimal.makeNullable() -> KnownTypesEnum.BIG_DECIMAL
            boolean, nullableBoolean -> KnownTypesEnum.BOOLEAN
            binary, binary.makeNullable() -> KnownTypesEnum.BINARY
            uuid, uuid.makeNullable() -> KnownTypesEnum.UUID
            else -> null
        }
    }

    enum class KnownTypesEnum {
        STRING,
        BOOLEAN,
        INTEGER,
        BIG_INTEGER,
        BIG_DECIMAL,
        DOUBLE,
        FLOAT,
        LONG,
        SHORT,
        BINARY,
        UUID
    }
}
