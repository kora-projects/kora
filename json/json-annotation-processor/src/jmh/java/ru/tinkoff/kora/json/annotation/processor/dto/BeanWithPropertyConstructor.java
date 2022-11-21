package ru.tinkoff.kora.json.annotation.processor.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.RandomStringUtils;
import ru.tinkoff.kora.json.common.annotation.Json;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Random;

@Json
public class BeanWithPropertyConstructor {
    private final int propA;
    private final String propB;
    private final long propC;
    private final SomeBean propD;
    private final SomeEnum propE;

    @JsonCreator
    public BeanWithPropertyConstructor(
        @JsonProperty("propA") int propA,
        @Nullable @JsonProperty("propB") String propB,
        @JsonProperty("propC") long propC,
        @Nullable @JsonProperty("propD") SomeBean propD,
        @Nullable @JsonProperty("propE") SomeEnum propE) {
        this.propA = propA;
        this.propB = propB;
        this.propC = propC;
        this.propD = propD;
        this.propE = propE;
    }

    public static BeanWithPropertyConstructor random(Random random) {
        return new BeanWithPropertyConstructor(
            random.nextInt(),
            RandomStringUtils.randomAscii(random.nextInt(32)),
            random.nextLong(),
            SomeBean.random(random),
            SomeEnum.values()[random.nextInt(SomeEnum.values().length)]
        );
    }

    public int getPropA() {
        return propA;
    }

    public String getPropB() {
        return propB;
    }

    public long getPropC() {
        return propC;
    }

    public SomeBean getPropD() {
        return propD;
    }

    public SomeEnum getPropE() {
        return propE;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BeanWithPropertyConstructor that = (BeanWithPropertyConstructor) o;
        return propA == that.propA && propC == that.propC && Objects.equals(propB, that.propB) && Objects.equals(propD, that.propD) && propE == that.propE;
    }

    @Override
    public int hashCode() {
        return Objects.hash(propA, propB, propC, propD, propE);
    }
}
