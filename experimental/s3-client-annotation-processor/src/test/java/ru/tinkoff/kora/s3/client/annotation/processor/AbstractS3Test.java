package ru.tinkoff.kora.s3.client.annotation.processor;

import org.intellij.lang.annotations.Language;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.s3.client.S3Client;
import ru.tinkoff.kora.s3.client.S3ClientFactory;

import java.util.ArrayList;
import java.util.List;

public class AbstractS3Test extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import java.nio.ByteBuffer;
            import java.io.InputStream;
            import java.util.List;
            import java.util.Iterator;
            import java.util.Collection;
            import java.util.Optional;
            import ru.tinkoff.kora.s3.client.annotation.*;
            import ru.tinkoff.kora.s3.client.annotation.S3.*;
            import ru.tinkoff.kora.s3.client.model.*;
            import ru.tinkoff.kora.s3.client.*;
            import ru.tinkoff.kora.s3.client.S3Client.*;
            """;
    }

    protected S3Client s3Client = Mockito.mock(S3Client.class);

    protected TestObject compile(@Language("java") String source, Object... addArgs) {
        var result = this.compile(List.of(new S3ClientAnnotationProcessor(), new AopAnnotationProcessor()), source);
        result.assertSuccess();
        var clientFactory = (S3ClientFactory) s3ClientClazz -> s3Client;
        var args = new ArrayList<Object>(1 + addArgs.length);
        args.add(clientFactory);
        args.addAll(List.of(addArgs));
        return new TestObject(loadClass("$Client_Impl"), args);
    }
}
