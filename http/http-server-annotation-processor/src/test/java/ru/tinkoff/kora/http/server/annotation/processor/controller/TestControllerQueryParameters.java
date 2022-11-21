package ru.tinkoff.kora.http.server.annotation.processor.controller;

import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Query;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

import static ru.tinkoff.kora.http.common.HttpMethod.GET;

@HttpController
public class TestControllerQueryParameters {
    /*
    Query: String, Integer, Long, Double, Boolean, Enum<?>, List<String>, List<Integer>, List<Long>, List<Double>, List<Boolean>, List<Enum<?>>
     */

    @HttpRoute(method = GET, path = "/queryString")
    void queryString(@Query("value") String value1) {
    }

    @HttpRoute(method = GET, path = "/queryNullableString")
    void queryNullableString(@Query @Nullable String value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalString")
    void queryOptionalString(@Query Optional<String> value) {
    }

    @HttpRoute(method = GET, path = "/queryStringList")
    void queryStringList(@Query List<String> value) {
    }


    @HttpRoute(method = GET, path = "/queryInteger")
    void queryInteger(@Query int value) {
    }

    @HttpRoute(method = GET, path = "/queryIntegerObject")
    void queryIntegerObject(@Query Integer value) {
    }

    @HttpRoute(method = GET, path = "/queryNullableInteger")
    void queryNullableInteger(@Query Integer value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalInteger")
    void queryOptionalInteger(@Query Optional<Integer> value) {
    }

    @HttpRoute(method = GET, path = "/queryIntegerList")
    void queryIntegerList(@Query List<Integer> value) {
    }


    @HttpRoute(method = GET, path = "/queryLong")
    void queryLong(@Query long value) {
    }

    @HttpRoute(method = GET, path = "/queryLongObject")
    void queryLongObject(@Query Long value) {
    }

    @HttpRoute(method = GET, path = "/queryNullableLong")
    void queryNullableLong(@Query Long value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalLong")
    void queryOptionalLong(@Query Optional<Long> value) {
    }

    @HttpRoute(method = GET, path = "/queryLongList")
    void queryLongList(@Query List<Long> value) {
    }


    @HttpRoute(method = GET, path = "/queryDouble")
    void queryDouble(@Query double value) {
    }

    @HttpRoute(method = GET, path = "/queryDoubleObject")
    void queryDoubleObject(@Query Double value) {
    }

    @HttpRoute(method = GET, path = "/queryNullableDouble")
    void queryNullableDouble(@Query Double value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalDouble")
    void queryOptionalDouble(@Query Optional<Double> value) {
    }

    @HttpRoute(method = GET, path = "/queryDoubleList")
    void queryDoubleList(@Query List<Double> value) {
    }


    @HttpRoute(method = GET, path = "/queryBoolean")
    void queryBoolean(@Query boolean value) {
    }

    @HttpRoute(method = GET, path = "/queryBooleanObject")
    void queryBooleanObject(@Query Boolean value) {
    }

    @HttpRoute(method = GET, path = "/queryNullableBoolean")
    void queryNullableBoolean(@Query Boolean value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalBoolean")
    void queryOptionalBoolean(@Query Optional<Boolean> value) {
    }

    @HttpRoute(method = GET, path = "/queryBooleanList")
    void queryBooleanList(@Query List<Boolean> value) {
    }


    @HttpRoute(method = GET, path = "/queryEnum")
    void queryEnum(@Query TestEnum value) {
    }

    @HttpRoute(method = GET, path = "/queryNullableEnum")
    void queryNullableEnum(@Query @Nullable TestEnum value) {
    }

    @HttpRoute(method = GET, path = "/queryOptionalEnum")
    void queryOptionalEnum(@Query Optional<TestEnum> value) {
    }

    @HttpRoute(method = GET, path = "/queryEnumList")
    void queryEnumList(@Query List<TestEnum> value) {
    }

}
