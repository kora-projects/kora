package ru.tinkoff.kora.database.symbol.processor.jdbc

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName

interface JdbcNativeType {
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
        ): JdbcNativeType {
            class Impl : JdbcNativeType {
                override fun type() = type
                override fun extract(rs: String, index: CodeBlock) = extract(rs, index)
                override fun bind(stmt: String, variableName: String, idx: Int) = bind(stmt, variableName, idx)
                override fun bindNull(stmt: String, idx: Int) = bindNull(stmt, idx)
            }
            return Impl()
        }
    }
}


