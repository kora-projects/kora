package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.s3.client.annotation.ObjectDelete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;

import java.util.Collection;
import java.util.List;

public interface AwsDeletesS3Client {

    // arguments
    @ObjectDelete
    void delete1(List<String> keys);

    @ObjectDelete
    void delete2(Collection<String> keys);

    // return
    @ObjectDelete
    void delete1r(List<String> keys);

    @ObjectDelete
    DeleteObjectsResponse delete2r(List<String> keys);
}
