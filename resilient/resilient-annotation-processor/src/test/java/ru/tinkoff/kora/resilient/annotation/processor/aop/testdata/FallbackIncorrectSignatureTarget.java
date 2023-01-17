package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.resilient.fallback.annotation.Fallback;

@Component
public class FallbackIncorrectSignatureTarget {

    public static final String VALUE = "OK";
    public static final String FALLBACK = "FALLBACK";

    public boolean alwaysFail = true;

    @Fallback(value = "custom_fallback1", method = "getFallbackSync")
    public String getValueSync(String arg1, String arg2) {
        if (alwaysFail)
            throw new IllegalStateException("Failed");

        return VALUE;
    }

    protected String getFallbackSync(String arg1, String arg2) {
        return FALLBACK + "-" + arg1 + "-" + arg2;
    }
}
