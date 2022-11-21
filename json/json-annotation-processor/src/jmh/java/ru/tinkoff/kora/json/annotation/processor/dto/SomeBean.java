package ru.tinkoff.kora.json.annotation.processor.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.lang3.RandomStringUtils;
import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.common.annotation.JsonReader;

import java.util.Objects;
import java.util.Random;

@Json
public class SomeBean {
    @JsonReader
    public SomeBean(int propA, String propB, long propC, SomeEnum propE) {
        this.propA = propA;
        this.propB = propB;
        this.propC = propC;
        this.propE = propE;
    }

    @JsonCreator
    public SomeBean() {
    }

    public int getPropA() {
        return propA;
    }

    public void setPropA(int propA) {
        this.propA = propA;
    }

    public String getPropB() {
        return propB;
    }

    public void setPropB(String propB) {
        this.propB = propB;
    }

    public long getPropC() {
        return propC;
    }

    public void setPropC(long propC) {
        this.propC = propC;
    }

    public SomeEnum getPropE() {
        return propE;
    }

    public void setPropE(SomeEnum propE) {
        this.propE = propE;
    }

    private int propA;
    private String propB;
    private long propC;
    private SomeEnum propE;

    public static SomeBean random(Random random) {
        final SomeBean result = new SomeBean();
        result.setPropA(random.nextInt());
        result.setPropB(RandomStringUtils.randomAscii(random.nextInt(32)));
        result.setPropC(random.nextLong());
        result.setPropE(SomeEnum.values()[random.nextInt(SomeEnum.values().length)]);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SomeBean someBean = (SomeBean) o;
        return propA == someBean.propA && propC == someBean.propC && Objects.equals(propB, someBean.propB) && propE == someBean.propE;
    }

    @Override
    public int hashCode() {
        return Objects.hash(propA, propB, propC, propE);
    }
}
