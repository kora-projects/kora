package ru.tinkoff.kora.common.naming;

/**
 * Allow changing naming strategy for fields
 */
public interface NameConverter {

    String convert(String originalName);
}
