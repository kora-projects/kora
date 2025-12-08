package ru.tinkoff.kora.aws.s3.annotation.processor;

import org.intellij.lang.annotations.Language;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.aws.s3.S3Client;
import ru.tinkoff.kora.aws.s3.S3ClientConfigWithCredentials;
import ru.tinkoff.kora.aws.s3.S3ClientFactory;

import java.util.ArrayList;
import java.util.List;

public class AbstractS3ClientTest extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import java.nio.ByteBuffer;
            import java.io.InputStream;
            import java.util.List;
            import java.util.Iterator;
            import java.util.Collection;
            import java.util.Optional;
            import ru.tinkoff.kora.aws.s3.annotation.*;
            import ru.tinkoff.kora.aws.s3.annotation.S3.*;
            import ru.tinkoff.kora.aws.s3.model.request.*;
            import ru.tinkoff.kora.aws.s3.model.response.*;
            import ru.tinkoff.kora.aws.s3.*;
            import ru.tinkoff.kora.aws.s3.S3Client.*;
            """;
    }

    protected S3Client s3Client = Mockito.mock(S3Client.class);
    protected S3ClientConfigWithCredentials config = Mockito.mock(S3ClientConfigWithCredentials.class);

    protected AbstractAnnotationProcessorTest.TestObject compile(@Language("java") String source, Object... addArgs) {
        var result = this.compile(List.of(new S3ClientAnnotationProcessor(), new AopAnnotationProcessor()), source);
        result.assertSuccess();
        var clientFactory = (S3ClientFactory) config -> s3Client;
        var args = new ArrayList<Object>(2 + addArgs.length);
        args.add(clientFactory);
        args.add(config);
        args.addAll(List.of(addArgs));
        return new AbstractAnnotationProcessorTest.TestObject(loadClass("$Client_ClientImpl"), args);
    }
}
