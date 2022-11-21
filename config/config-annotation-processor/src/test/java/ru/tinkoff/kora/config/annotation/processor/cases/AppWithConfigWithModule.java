package ru.tinkoff.kora.config.annotation.processor.cases;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.DefaultConfigExtractorsModule;

@KoraApp
public interface AppWithConfigWithModule extends DefaultConfigExtractorsModule {
    default Config testConfig() {
        return ConfigFactory.parseString("""
            pojo {
                intField = 1
                boxedIntField = 2
                longField = 3
                boxedLongField = 4
                doubleField = 5
                boxedDoubleField = 6
                booleanField = true
                boxedBooleanField = false
                stringField = "some string value"
                listField = "1,2,3,4,5"
                objectField {
                  foo = 1
                  bar = baz
                }
                props {
                  foo.bar.baz1 = 1
                  foo.bar.baz2 = true
                  foo.bar.baz3 = "asd"
                  foo.bar.baz4 = [1, false, "zxc"]
                }
            }
                            
            rec {
                intField = 1
                boxedIntField = 2
                longField = 3
                boxedLongField = 4
                doubleField = 5
                boxedDoubleField = 6
                booleanField = true
                boxedBooleanField = false
                stringField = "some string value"
                listField = "1,2,3,4,5"
                objectField {
                  foo = 1
                  bar = baz
                }
                props {
                  foo.bar.baz1 = 1
                  foo.bar.baz2 = true
                  foo.bar.baz3 = "asd"
                  foo.bar.baz4 = [1, false, "zxc"]
                }
            }
            """
        ).resolve();
    }

    default MockLifecycle object(@Tag(RecordConfig.class) ValueOf<RecordConfig> recordConfig, @Tag(PojoConfig.class) ValueOf<PojoConfig> pojoConfig) {
        return new MockLifecycle() {};
    }
}
