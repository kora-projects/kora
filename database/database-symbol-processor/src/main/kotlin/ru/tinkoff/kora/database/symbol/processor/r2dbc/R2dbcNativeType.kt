package ru.tinkoff.kora.database.symbol.processor.r2dbc

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName


interface R2dbcNativeType {
    fun type(): TypeName
    fun extract(rs: String, index: CodeBlock): CodeBlock
    fun bind(stmt: String, variableName: String, idx: Int): CodeBlock
    fun bindNull(stmt: String, idx: Int): CodeBlock

    companion object {
        fun of(
            type: TypeName,
            extract: (String, CodeBlock) -> CodeBlock,
            bind: (String, String, Int) -> CodeBlock,
            bindNull: (String, Int) -> CodeBlock,
        ): R2dbcNativeType {
            class Impl : R2dbcNativeType {
                override fun type() = type
                override fun extract(rs: String, index: CodeBlock) = extract(rs, index)
                override fun bind(stmt: String, variableName: String, idx: Int) = bind(stmt, variableName, idx)
                override fun bindNull(stmt: String, idx: Int) = bindNull(stmt, idx)
            }
            return Impl()
        }

        fun of(type: TypeName, javaType: TypeName): R2dbcNativeType {
            return of(
                type,
                { rsName, i -> CodeBlock.of("%L.get(%L, %T::class.javaObjectType)", rsName, i, javaType) },
                { stmt, variableName, idx -> CodeBlock.of("%L.bind(%L, %L)", stmt, idx, variableName) },
                { stmt, idx -> CodeBlock.of("%L.bindNull(%L, %T::class.javaObjectType)", stmt, idx, javaType) }
            )
        }

        fun of(type: TypeName) = of(type, type.copy(false))
    }
}

