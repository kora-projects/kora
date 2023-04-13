package ru.tinkoff.kora.json.annotation.processor.extension.module;

import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.json.annotation.processor.dto.*;
import ru.tinkoff.kora.json.common.JsonCommonModule;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;

@KoraApp
public interface TestModule extends JsonCommonModule {
    default Lifecycle stub(
        JsonReader<DtoOnlyReader> dtoOnlyReaderReader,
        JsonWriter<DtoOnlyWriter> dtoOnlyWriterJsonWriter,
        JsonReader<DtoWithInnerDto> dtoWithInnerDtoJsonReader,
        JsonWriter<DtoWithInnerDto> dtoWithInnerDtoJsonWriter,
        JsonReader<DtoWithJsonFieldWriter> dtoWithJsonFieldWriterJsonReader,
        JsonWriter<DtoWithJsonFieldWriter> dtoWithJsonFieldWriterJsonWriter,
        JsonReader<DtoWithJsonSkip> dtoWithJsonSkipJsonReader,
        JsonWriter<DtoWithJsonSkip> dtoWithJsonSkipJsonWriter,
        JsonReader<DtoWithSupportedTypes> dtoWithSupportedTypesJsonReader,
        JsonWriter<DtoWithSupportedTypes> dtoWithSupportedTypesJsonWriter
    ) {
        return null;
    }
}
