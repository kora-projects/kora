package io.koraframework.validation.annotation.processor.testdata;

import org.jspecify.annotations.Nullable;
import io.koraframework.validation.common.annotation.NotBlank;
import io.koraframework.validation.common.annotation.Size;
import io.koraframework.validation.common.annotation.Valid;

import java.util.List;

@Valid
public class ValidBar {

    public static final String IGNORED = "ops";

    @Nullable
    @NotBlank
    @Size(max = 50)
    private String id;
    @Size(max = 5, min = 1)
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
