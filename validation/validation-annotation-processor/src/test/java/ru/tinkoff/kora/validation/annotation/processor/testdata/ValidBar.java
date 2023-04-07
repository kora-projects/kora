package ru.tinkoff.kora.validation.annotation.processor.testdata;

import ru.tinkoff.kora.validation.common.annotation.NotBlank;
import ru.tinkoff.kora.validation.common.annotation.Size;
import ru.tinkoff.kora.validation.common.annotation.Valid;

import javax.annotation.Nullable;
import java.util.List;

@Valid
public class ValidBar {

    public static final String IGNORED = "ops";

    @Nullable
    @NotBlank
    private String id;
    @Size(min = 1, max = 5)
    private List<Integer> codes;
    @Valid
    private List<ValidTaz> tazs;

    public String getId() {
        return id;
    }

    public ValidBar setId(String id) {
        this.id = id;
        return this;
    }

    public List<Integer> getCodes() {
        return codes;
    }

    public ValidBar setCodes(List<Integer> codes) {
        this.codes = codes;
        return this;
    }

    public List<ValidTaz> getTazs() {
        return tazs;
    }

    public ValidBar setTazs(List<ValidTaz> tazs) {
        this.tazs = tazs;
        return this;
    }
}
