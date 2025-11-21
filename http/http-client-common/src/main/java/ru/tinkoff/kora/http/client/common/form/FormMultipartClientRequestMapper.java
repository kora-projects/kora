package ru.tinkoff.kora.http.client.common.form;

import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.form.FormMultipart;

public final class FormMultipartClientRequestMapper implements HttpClientRequestMapper<FormMultipart> {
    @Override
    public HttpBodyOutput apply(FormMultipart value) {
        return MultipartWriter.write(value.parts());
    }
}
