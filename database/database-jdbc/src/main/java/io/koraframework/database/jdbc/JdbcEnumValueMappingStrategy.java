package io.koraframework.database.jdbc;

@FunctionalInterface
public interface JdbcEnumValueMappingStrategy {

    String map(String enumName);

    static JdbcEnumValueMappingStrategy asIs() {
        return enumName -> enumName;
    }

    static JdbcEnumValueMappingStrategy lowerCasing() {
        return String::toLowerCase;
    }

    static JdbcEnumValueMappingStrategy upperCasing() {
        return String::toUpperCase;
    }
}
