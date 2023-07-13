package ru.tinkoff.kora.config.symbol.processor.cases;

import javax.annotation.Nullable;
import java.util.Objects;

public class PojoConfig {
    private String value1;

    @Nullable
    private String value2;

    public String getValue1() {
        return value1;
    }

    public void setValue1(String value1) {
        this.value1 = value1;
    }

    @Nullable
    public String getValue2() {
        return value2;
    }

    public void setValue2(@Nullable String value2) {
        this.value2 = value2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PojoConfig that = (PojoConfig) o;
        return Objects.equals(value1, that.value1) && Objects.equals(value2, that.value2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value1, value2);
    }
}
