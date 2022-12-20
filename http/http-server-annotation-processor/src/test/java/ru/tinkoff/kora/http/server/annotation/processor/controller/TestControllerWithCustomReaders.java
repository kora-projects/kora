package ru.tinkoff.kora.http.server.annotation.processor.controller;

import ru.tinkoff.kora.http.common.annotation.Header;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Path;
import ru.tinkoff.kora.http.common.annotation.Query;
import ru.tinkoff.kora.http.server.common.HttpServerResponseEntity;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.http.common.HttpMethod.GET;

@HttpController
public class TestControllerWithCustomReaders {

    @HttpRoute(method = GET, path = "/test/{pathEntity}")
    public HttpServerResponseEntity<String> test(@Path("pathEntity") ReadableEntity pathEntity,
                                                 @Nullable @Query("queryEntity") List<ReadableEntity> queryList,
                                                 @Header("header-Entity") Optional<ReadableEntity> headerEntity) {
        var resultList = queryList == null ? new ArrayList<ReadableEntity>() : new ArrayList<>(queryList);
        resultList.add(pathEntity);
        return new HttpServerResponseEntity<>(200, resultList.stream().map(ReadableEntity::string).collect(Collectors.joining(", ")));
    }
}
