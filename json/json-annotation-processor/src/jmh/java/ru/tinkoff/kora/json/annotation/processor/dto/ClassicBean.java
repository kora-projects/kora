package ru.tinkoff.kora.json.annotation.processor.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.common.annotation.JsonField;
import ru.tinkoff.kora.json.common.annotation.JsonReader;

import java.util.Objects;

@Json
public final class ClassicBean {
    @JsonProperty("a")
    @JsonField("a")
    public int a;
    @JsonProperty("b")
    @JsonField("b")
    public int b;
    @JsonProperty("c")
    @JsonField("c")
    public int c123;
    @JsonProperty("d")
    @JsonField("d")
    public int d;
    @JsonProperty("e")
    @JsonField("e")
    public int e;
    @JsonProperty("f")
    @JsonField("f")
    public int foobar;
    @JsonProperty("g")
    @JsonField("g")
    public int g;
    @JsonProperty("h")
    @JsonField("h")
    public int habitus;

    @JsonCreator
    public ClassicBean() {
    }

    @JsonReader
    public ClassicBean(
        int a,
        int b,
        int c123,
        int d,
        int e,
        int foobar,
        int g,
        int habitus) {
        this.a = a;
        this.b = b;
        this.c123 = c123;
        this.d = d;
        this.e = e;
        this.foobar = foobar;
        this.g = g;
        this.habitus = habitus;
    }

    public ClassicBean setUp() {
        a = 1;
        b = 999;
        c123 = -1000;
        d = 13;
        e = 6;
        foobar = -33;
        g = 0;
        habitus = 123456789;
        return this;
    }

    public int getA() {
        return a;
    }

    public void setA(int a) {
        this.a = a;
    }

    public int getB() {
        return b;
    }

    public void setB(int b) {
        this.b = b;
    }

    public int getC123() {
        return c123;
    }

    public void setC123(int c123) {
        this.c123 = c123;
    }

    public int getD() {
        return d;
    }

    public void setD(int d) {
        this.d = d;
    }

    public int getE() {
        return e;
    }

    public void setE(int e) {
        this.e = e;
    }

    public int getFoobar() {
        return foobar;
    }

    public void setFoobar(int foobar) {
        this.foobar = foobar;
    }

    public int getG() {
        return g;
    }

    public void setG(int g) {
        this.g = g;
    }

    public int getHabitus() {
        return habitus;
    }

    public void setHabitus(int habitus) {
        this.habitus = habitus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassicBean that = (ClassicBean) o;
        return a == that.a && b == that.b && c123 == that.c123 && d == that.d && e == that.e && foobar == that.foobar && g == that.g && habitus == that.habitus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, c123, d, e, foobar, g, habitus);
    }
}
