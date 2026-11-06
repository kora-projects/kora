package io.koraframework.logging.common.masking;

import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.writer.EnumJsonWriter;
import io.koraframework.logging.common.arg.MaskedStructuredArgumentMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingJsonGeneratorTest {

    enum Status {ACTIVE, BLOCKED}

    record Account(String login, byte[] secret, Status status) {}

    record Holder(Account account, String visible) {}

    private static final JsonWriter<Status> STATUS_WRITER = new EnumJsonWriter<>(Status.values(), Enum::name, (gen, s) -> gen.writeString(s));

    // the same calls the Kora JSON processor generates: writeBinary for byte[], EnumJsonWriter (writeRawValue) for enums
    private static final JsonWriter<Account> ACCOUNT_WRITER = (gen, a) -> {
        gen.writeStartObject();
        gen.writeName("login");
        gen.writeString(a.login());
        gen.writeName("secret");
        gen.writeBinary(a.secret());
        gen.writeName("status");
        STATUS_WRITER.write(gen, a.status());
        gen.writeEndObject();
    };

    private static final JsonWriter<Holder> HOLDER_WRITER = (gen, h) -> {
        gen.writeStartObject();
        gen.writeName("account");
        ACCOUNT_WRITER.write(gen, h.account());
        gen.writeName("visible");
        gen.writeString(h.visible());
        gen.writeEndObject();
    };

    private static final Account ACCOUNT = new Account("bob", "top-secret".getBytes(StandardCharsets.UTF_8), Status.BLOCKED);

    @Test
    void masksBinaryAndEnumFields() {
        var rules = MaskingRules.builder(Account.class)
            .mask("secret", new MaskingFull())
            .mask("status", new MaskingFull())
            .build();
        var mapper = new MaskedStructuredArgumentMapper<>(ACCOUNT_WRITER, rules, true);

        var out = mapper.writeToString(ACCOUNT);

        assertThat(out).isEqualTo("{\"login\":\"bob\",\"secret\":\"***\",\"status\":\"***\"}");
    }

    @Test
    void passesUnquotedEnumValueToStrategy() {
        var rules = MaskingRules.builder(Account.class)
            .mask("status", new MaskingKeepFirst("***", 2))
            .build();
        var mapper = new MaskedStructuredArgumentMapper<>(ACCOUNT_WRITER, rules, true);

        var out = mapper.writeToString(ACCOUNT);

        assertThat(out).isEqualTo("{\"login\":\"bob\",\"secret\":\"dG9wLXNlY3JldA==\",\"status\":\"BL***\"}");
    }

    @Test
    void skipsBinaryAndEnumValuesInsideMaskedObject() {
        var rules = MaskingRules.builder(Holder.class)
            .mask("account", new MaskingFull())
            .build();
        var mapper = new MaskedStructuredArgumentMapper<>(HOLDER_WRITER, rules, true);

        var out = mapper.writeToString(new Holder(ACCOUNT, "value"));

        assertThat(out)
            .doesNotContain("bob", "dG9wLXNlY3JldA==", "BLOCKED")
            .endsWith(",\"visible\":\"value\"}");
    }
}
