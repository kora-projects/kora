package ru.tinkoff.kora.test.extension.junit5;

import io.mockk.MockKKt;
import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;

final class MockUtils {

    private static final boolean isMockitoAvailable = isClassPresent("org.mockito.internal.util.MockUtil");
    private static final boolean isMockkAvailable = isClassPresent("io.mockk.MockKKt");

    private MockUtils() {
    }

    private static boolean isClassPresent(String className) {
        try {
            return KoraJUnit5Extension.class.getClassLoader().loadClass(className) != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static boolean haveAnyMockEngine() {
        return isMockitoAvailable || isMockkAvailable;
    }

    static void resetIfMock(Object mockCandidate) {
        if (isMockitoAvailable) {
            if (MockUtil.isMock(mockCandidate) || MockUtil.isSpy(mockCandidate)) {
                Mockito.reset(mockCandidate);
            }
        }

        if (isMockkAvailable) {
            MockKKt.clearAllMocks(true, true, true, true, true, true, true);
        }
    }
}
