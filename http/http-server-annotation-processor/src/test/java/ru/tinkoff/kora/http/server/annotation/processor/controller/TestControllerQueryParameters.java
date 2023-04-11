package ru.tinkoff.kora.http.server.annotation.processor.controller;

import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Query;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static ru.tinkoff.kora.http.common.HttpMethod.GET;

@HttpController
public class TestControllerQueryParameters {
    /*
    Query: String, Integer, Long, Double, Boolean, Enum<?>, List<String>, List<Integer>, List<Long>, List<Double>, List<Boolean>, List<Enum<?>>
     */

    @HttpRoute(method = GET, path = "/queryString")
    public void queryString(@Query("value") String value1) {
    }

    @HttpRoute(method = GET, path = "/queryNullableString")
    public void queryNullableString(@Query @Nullable String value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalString")
    public void queryOptionalString(@Query Optional<String> value) {
    }

    @HttpRoute(method = GET, path = "/queryStringList")
    public void queryStringList(@Query List<String> value) {
    }


    @HttpRoute(method = GET, path = "/queryInteger")
    public void queryInteger(@Query int value) {
    }

    @HttpRoute(method = GET, path = "/queryIntegerObject")
    public void queryIntegerObject(@Query Integer value) {
    }

    @HttpRoute(method = GET, path = "/queryNullableInteger")
    public void queryNullableInteger(@Query Integer value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalInteger")
    public void queryOptionalInteger(@Query Optional<Integer> value) {
    }

    @HttpRoute(method = GET, path = "/queryIntegerList")
    public void queryIntegerList(@Query List<Integer> value) {
    }


    @HttpRoute(method = GET, path = "/queryLong")
    public void queryLong(@Query long value) {
    }

    @HttpRoute(method = GET, path = "/queryLongObject")
    public void queryLongObject(@Query Long value) {
    }

    @HttpRoute(method = GET, path = "/queryNullableLong")
    public void queryNullableLong(@Query Long value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalLong")
    public void queryOptionalLong(@Query Optional<Long> value) {
    }

    @HttpRoute(method = GET, path = "/queryLongList")
    public void queryLongList(@Query List<Long> value) {
    }


    @HttpRoute(method = GET, path = "/queryDouble")
    public void queryDouble(@Query double value) {
    }

    @HttpRoute(method = GET, path = "/queryDoubleObject")
    public void queryDoubleObject(@Query Double value) {
    }

    @HttpRoute(method = GET, path = "/queryNullableDouble")
    public void queryNullableDouble(@Query Double value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalDouble")
    public void queryOptionalDouble(@Query Optional<Double> value) {
    }

    @HttpRoute(method = GET, path = "/queryDoubleList")
    public void queryDoubleList(@Query List<Double> value) {
    }


    @HttpRoute(method = GET, path = "/queryBoolean")
    public void queryBoolean(@Query boolean value) {
    }

    @HttpRoute(method = GET, path = "/queryBooleanObject")
    public void queryBooleanObject(@Query Boolean value) {
    }

    @HttpRoute(method = GET, path = "/queryNullableBoolean")
    public void queryNullableBoolean(@Query Boolean value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalBoolean")
    public void queryOptionalBoolean(@Query Optional<Boolean> value) {
    }

    @HttpRoute(method = GET, path = "/queryBooleanList")
    public void queryBooleanList(@Query List<Boolean> value) {
    }


    @HttpRoute(method = GET, path = "/queryEnum")
    public void queryEnum(@Query TestEnum value) {
    }

    @HttpRoute(method = GET, path = "/queryNullableEnum")
    public void queryNullableEnum(@Query @Nullable TestEnum value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalEnum")
    public void queryOptionalEnum(@Query Optional<TestEnum> value) {
    }

    @HttpRoute(method = GET, path = "/queryEnumList")
    public void queryEnumList(@Query List<TestEnum> value) {
        Objects.requireNonNull(value);
    }

    @HttpRoute(method = GET, path = "/queryNullableEnumList")
    public void queryNullableEnumList(@Nullable @Query List<TestEnum> value) {
    }

}
