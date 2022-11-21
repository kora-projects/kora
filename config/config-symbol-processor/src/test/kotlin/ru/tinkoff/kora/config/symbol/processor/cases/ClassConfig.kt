package ru.tinkoff.kora.config.symbol.processor.cases

import java.util.*

class ClassConfig(
    val intField: Int,
    val boxedIntField: Int?,
    val longField: Long,
    val boxedLongField: Long,
    val doubleField: Double,
    val boxedDoubleField: Double,
    val booleanField: Boolean,
    val boxedBooleanField: Boolean,
    val stringField: String,
    val listField: List<Int>,
    val objectField: SomeConfig,
    val props: Properties
) {
    override fun equals(other: Any?): Boolean {
        if (other !is ClassConfig) return false
        return  this.intField == other.intField &&
            this.boxedIntField == other.boxedIntField &&
            this.longField == other.longField &&
            this.boxedLongField == other.boxedLongField &&
            this.doubleField == other.doubleField &&
            this.boxedDoubleField == other.boxedDoubleField &&
            this.booleanField == other.booleanField &&
            this.boxedBooleanField == other.boxedBooleanField &&
            this.stringField == other.stringField &&
            this.listField == other.listField &&
            this.objectField == other.objectField &&
            this.props == other.props
    }

    override fun hashCode(): Int {
        var result = intField
        result = 31 * result + (boxedIntField ?: 0)
        result = 31 * result + longField.hashCode()
        result = 31 * result + boxedLongField.hashCode()
        result = 31 * result + doubleField.hashCode()
        result = 31 * result + boxedDoubleField.hashCode()
        result = 31 * result + booleanField.hashCode()
        result = 31 * result + boxedBooleanField.hashCode()
        result = 31 * result + stringField.hashCode()
        result = 31 * result + listField.hashCode()
        result = 31 * result + objectField.hashCode()
        result = 31 * result + props.hashCode()
        return result
    }
}
