package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.json.common.annotation.Json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Json
public record DtoWithSupportedTypes(
    String string,
    Boolean booleanObject,
    boolean booleanPrimitive,
    Integer integerObject,
    int integerPrimitive,
    BigInteger bigInteger,
    BigDecimal bigDecimal,
    Double doubleObject,
    double doublePrimitive,
    Float floatObject,
    float floatPrimitive,
    Long longObject,
    long longPrimitive,
    Short shortObject,
    short shortPrimitive,
    byte[] binary,
    LocalDate localDate,
    List<Integer> listOfInteger,
    Set<Integer> setOfInteger) {

}
