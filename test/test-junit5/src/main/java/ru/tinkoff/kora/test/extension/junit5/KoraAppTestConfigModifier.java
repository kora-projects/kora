package ru.tinkoff.kora.test.extension.junit5;

/**
 * Is useful when some part of configuration should be modified before test execution
 * Example: Can be used to change config using TestContainer container ports
 */
public interface KoraAppTestConfigModifier {

    /**
     * @return Kora Config modification for {@link KoraAppTest} tests
     */
    KoraConfigModification config();
}
