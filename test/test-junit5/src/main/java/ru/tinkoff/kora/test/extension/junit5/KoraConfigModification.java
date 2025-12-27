package ru.tinkoff.kora.test.extension.junit5;

import java.util.Map;

/**
 * "application.conf" configuration modification for tests with system properties
 */
public interface KoraConfigModification {

    Map<String, String> systemProperties();

    /**
     * Example: Given config below
     * <pre>
     * {@code
     *  myconfig {
     *      myinnerconfig {
     *          first = ${ENV_FIRST}
     *          second = ${ENV_SECOND}
     *      }
     * }
     * }
     * </pre>
     * Use system property to set `ENV_FIRST` and 'ENV_SECOND'
     * <pre>
     * {@code
     * KoraConfigModification.ofString("""
     *                              myconfig {
     *                                  myinnerconfig {
     *                                      first = ${ENV_FIRST}
     *                                      second = ${ENV_SECOND}
     *                                  }
     *                              }
     *                              """)
     *                          .withSystemProperty("ENV_FIRST", "1")
     *                          .withSystemProperty("ENV_SECOND", "2");
     * }
     * </pre>
     *
     * @param key   system property key
     * @param value system property value
     * @return self
     */
    KoraConfigModification withSystemProperty(String key, String value);

    /**
     * Example: Given config below
     * <pre>
     * {@code
     *  myconfig {
     *      myinnerconfig {
     *          first = ${ENV_FIRST}
     *          second = ${ENV_SECOND}
     *      }
     * }
     * }
     * </pre>
     * <p>
     * Use system property to set `ENV_FIRST` and 'ENV_SECOND' at once
     * <pre>
     * {@code
     * KoraConfigModification.ofString("""
     *                              myconfig {
     *                                  myinnerconfig {
     *                                      first = ${ENV_FIRST}
     *                                      second = ${ENV_SECOND}
     *                                  }
     *                              }
     *                              """)
     *                          .withSystemProperties(Map.of("ENV_FIRST", "1", "ENV_SECOND", "2"));
     * }
     * </pre>
     *
     * @param systemProperties map of properties to add, must not contain nulls in keys or values
     * @return self
     */
    default KoraConfigModification withSystemProperties(Map<String, String> systemProperties) {
        systemProperties.forEach(this::withSystemProperty);
        return this;
    }

    /**
     * Example below:
     * <pre>
     * {@code
     * KoraConfigModification.ofString("""
     *                              myconfig {
     *                                  myinnerconfig {
     *                                      first = 1
     *                                  }
     *                              }
     *                              """)
     * }
     * </pre>
     *
     * @param config application configuration with config as string
     * @return self
     */
    static KoraConfigModification ofString(String config) {
        return new KoraConfigString(config);
    }

    /**
     * File is located in "resources" directory than example below:
     * <pre>
     * {@code
     * KoraConfigModification.ofFile("reference-raw.conf")
     * }
     * </pre>
     *
     * @param configFile application configuration with config file from "resources" directory
     * @return self
     */
    static KoraConfigModification ofResourceFile(String configFile) {
        return new KoraConfigFile(configFile);
    }

    /**
     * Use system property to set `ENV_FIRST` and 'ENV_SECOND'
     * <pre>
     * {@code
     * KoraConfigModification.ofSystemProperty("ENV_FIRST", "1")
     *                      .withSystemProperty("ENV_SECOND", "2");
     * }
     * </pre>
     *
     * @param key   system property key
     * @param value system property value
     * @return self
     */
    static KoraConfigModification ofSystemProperty(String key, String value) {
        return new KoraConfigSystemProperties().withSystemProperty(key, value);
    }
}
