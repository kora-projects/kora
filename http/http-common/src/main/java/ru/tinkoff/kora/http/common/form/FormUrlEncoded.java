package ru.tinkoff.kora.http.common.form;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

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
