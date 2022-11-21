package ru.tinkoff.kora.config.annotation.processor.cases;

import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.ConfigRoot;

@ConfigRoot(AppWithConfigWithModule.class)
public class PojoConfigRootWithComponentOf {
    private final PojoConfig pojo;
    @Tag(RecordConfig.class)
    private final RecordConfig rec;

    public PojoConfigRootWithComponentOf(PojoConfig pojo, RecordConfig rec) {
        this.pojo = pojo;
        this.rec = rec;
    }

    @Tag(PojoConfig.class)
    public PojoConfig getPojo() {
        return pojo;
    }

    public RecordConfig getRec() {
        return rec;
    }
}
