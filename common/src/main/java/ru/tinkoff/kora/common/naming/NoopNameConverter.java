package ru.tinkoff.kora.common.naming;

public class NoopNameConverter implements NameConverter{
    @Override
    public String convert(String originalName) {
        return originalName;
    }
}
