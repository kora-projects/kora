package ru.tinkoff.kora.config.annotation.processor.cases;

import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.ConfigRoot;

@ConfigRoot
public class PojoConfigRoot {
    private final PojoConfig pojo;
    @Tag(RecordConfig.class)
    private final RecordConfig rec;

    public PojoConfigRoot(PojoConfig pojo, RecordConfig rec) {
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
