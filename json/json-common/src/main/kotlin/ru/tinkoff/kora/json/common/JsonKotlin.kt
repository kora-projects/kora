package ru.tinkoff.kora.json.common

object JsonKotlin {
    @Suppress("UNCHECKED_CAST")
    fun <T> writerForNullable(writer: JsonWriter<T>): JsonWriter<T?> {
        return writer as JsonWriter<T?>
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> readerForNullable(reader: JsonReader<T>): JsonReader<T?> {
        return reader as JsonReader<T?>
    }
}
