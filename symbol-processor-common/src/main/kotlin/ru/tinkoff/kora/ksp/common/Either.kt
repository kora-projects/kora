package ru.tinkoff.kora.ksp.common

sealed interface Either<out L, out R> {
    data class Left<L>(val value: L) : Either<L, Nothing>
    data class Right<L>(val value: L) : Either<Nothing, L>

    companion object {
        fun <L, R> left(left: L): Either<L, R> = Left(left)
        fun <L, R> right(right: R): Either<L, R> = Right(right)
    }
}
