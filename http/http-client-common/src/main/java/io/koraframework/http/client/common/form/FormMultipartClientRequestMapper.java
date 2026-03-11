package io.koraframework.http.client.common.form;

import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.form.FormMultipart;

public final class FormMultipartClientRequestMapper implements HttpClientRequestMapper<FormMultipart> {
    @Override
    public HttpBodyOutput apply(FormMultipart value) {
        return MultipartWriter.write(value.parts());
    }
}
