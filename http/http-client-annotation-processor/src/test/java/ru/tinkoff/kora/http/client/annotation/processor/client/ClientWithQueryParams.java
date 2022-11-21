package ru.tinkoff.kora.http.client.annotation.processor.client;


import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.Header;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Query;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;

@HttpClient(configPath = "clientWithQueryParams")
public interface ClientWithQueryParams {
    @HttpRoute(method = HttpMethod.POST, path = "/test1?test=test")
    void test1(@Query String test1);

    @HttpRoute(method = HttpMethod.POST, path = "/test2?")
    void test2(@Query("test2") String test);

    @HttpRoute(method = HttpMethod.POST, path = "/test3")
    void test3(@Query("test3") String test);

    @HttpRoute(method = HttpMethod.POST, path = "/test4")
    void test4(@Query("test4") String test4, @Nullable @Query("test") String test);

    @HttpRoute(method = HttpMethod.POST, path = "/test5")
    void test5(@Query String test51, @Query String test52, @Query String test53, @Query String test54, @Nullable @Query String test55, @Nullable @Query String test56);

    @HttpRoute(method = HttpMethod.POST, path = "/test6")
    void test6(@Nullable @Query String test61, @Nullable @Query String test62, @Nullable @Query String test63);

    @HttpRoute(method = HttpMethod.POST, path = "/nonStringParams")
    void nonStringParams(@Query int query1, @Query Integer query2);

    @HttpRoute(method = HttpMethod.POST, path = "/multipleParams")
    void multipleParams(@Query List<String> query1, @Nullable @Query List<Integer> query2);


}
