package ru.tinkoff.kora.test.extension.junit5;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * "application.conf" configuration modification for tests with system properties
 */
public interface KoraConfigModification {

    @Nonnull
    Map<String, String> systemProperties();

    /**
     * Example: Given config below
     * <pre>
     *  myconfig {
     *   myinnerconfig {
     *     first = ${ENV_FIRST}
     *     second = ${ENV_SECOND}
     *   }
     * }
     * </pre>
     * Use system property to set `ENV_FIRST` and 'ENV_SECOND'
     * <code>
     *      KoraConfigModification.ofString("""
     *                                      myconfig {
     *                                        myinnerconfig {
     *                                          first = ${ENV_FIRST}
     *                                          second = ${ENV_SECOND}
     *                                        }
     *                                      }
     *                                      """)
     *                                      .withSystemProperty("ENV_FIRST", "1")
     *                                      .withSystemProperty("ENV_SECOND", "2");
     * </code>
     *
     * @param key system property key
     * @param value system property value
     * @return self
     */
    @Nonnull
    KoraConfigModification withSystemProperty(@Nonnull String key, @Nonnull String value);

    /**
     * Example below:
     * <code>
     *      KoraConfigModification.ofString("""
     *                                      myconfig {
     *                                        myinnerconfig {
     *                                          first = 1
     *                                        }
     *                                      }
     *                                      """)
     * </code>
     *
     * @param config application configuration with config as string
     * @return self
     */
    @Nonnull
    static KoraConfigString ofString(@Nonnull String config) {
        return new KoraConfigString(config);
    }

    /**
     * File is located in "resources" directory than example below:
     * <code>
     *      KoraConfigModification.ofFile("reference-raw.conf")
     * </code>
     *
     * @param configFile application configuration with config file from "resources" directory
     * @return self
     */
    @Nonnull
    static KoraConfigFile ofResourceFile(@Nonnull String configFile) {
        return new KoraConfigFile(configFile);
    }

    /**
     * Use system property to set `ENV_FIRST` and 'ENV_SECOND'
     * <code>
     *     KoraConfigModification.ofSystemProperty("ENV_FIRST", "1")
     *                           .withSystemProperty("ENV_SECOND", "2");
     * </code>
     *
     * @param key system property key
     * @param value system property value
     * @return self
     */
    @Nonnull
    static KoraConfigSystemProperties ofSystemProperty(@Nonnull String key, @Nonnull String value) {
        return new KoraConfigSystemProperties().withSystemProperty(key, value);
    }
}
