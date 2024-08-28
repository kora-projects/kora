package ru.tinkoff.kora.http.common.form;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * <b>Русский</b>: Описывает тело HTTP запроса/ответа как форма с текстовыми ASCII данными
 * <hr>
 * <b>English</b>: Describes the HTTP request/response body as a form with ASCII text data
 * <br>
 * <br>
 * <a href="https://ru.wikipedia.org/wiki/Multipart/form-data">Описание Формы</a>
 */
public class FormUrlEncoded implements Iterable<FormUrlEncoded.FormPart> {
    public record FormPart(String name, List<String> values) {
        public FormPart(String name, String value) {
            this(name, List.of(value));
        }
    }

    private final Map<String, FormPart> parts;

    public FormUrlEncoded(Map<String, FormPart> parts) {
        this.parts = parts;
    }

    public FormUrlEncoded(List<FormPart> parts) {
        this(toMap(parts));
    }

    public FormUrlEncoded(FormPart... parts) {
        this(toMap(List.of(parts)));
    }

    private static Map<String, FormPart> toMap(Iterable<FormPart> parts) {
        var map = new HashMap<String, FormPart>();
        for (var part : parts) {
            var oldPart = map.putIfAbsent(part.name(), part);
            if (oldPart != null) {
                var newList = new ArrayList<String>(part.values.size() + oldPart.values.size());
                newList.addAll(part.values);
                newList.addAll(oldPart.values);
                map.put(part.name, new FormPart(part.name, newList));
            }
        }
        return map;
    }

    @Nonnull
    @Override
    public Iterator<FormPart> iterator() {
        return this.parts.values().iterator();
    }

    @Nullable
    public FormPart get(String name) {
        return this.parts.get(name);
    }
}
