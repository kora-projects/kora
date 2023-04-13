package ru.tinkoff.kora.json.common

object JsonKotlin {
    @SuppressWarnings("unchecked")
    fun <T> writerForNullable(writer: JsonWriter<T>): JsonWriter<T?> {
        return writer as JsonWriter<T?>
    }

    @SuppressWarnings("unchecked")
    fun <T> readerForNullable(reader: JsonReader<T>): JsonReader<T?> {
        return reader as JsonReader<T?>
    }
}
