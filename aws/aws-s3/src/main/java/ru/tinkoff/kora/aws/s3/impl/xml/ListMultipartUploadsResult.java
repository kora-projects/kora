package ru.tinkoff.kora.aws.s3.impl.xml;

import jakarta.annotation.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

//<ListMultipartUploadsResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
//    <Bucket>test</Bucket>
//    <KeyMarker></KeyMarker>
//    <UploadIdMarker></UploadIdMarker>
//    <NextKeyMarker></NextKeyMarker>
//    <NextUploadIdMarker></NextUploadIdMarker>
//    <Prefix></Prefix>
//    <MaxUploads>10000</MaxUploads>
//    <IsTruncated>false</IsTruncated>
//    <Upload>
//        <Key>588e899f-9a81-4033-b998-e3d884e0fcfb/677f6ee3-4757-4f80-9562-7eda561748ec</Key>
//        <UploadId>NTVhYjE3MTYtYmFiMy00OGM5LTkxODAtNTk5MDUyZTU3N2UzLjNiZjlmYjkyLTJhOTItNDllNi1iMjI4LTk4ZWQyOTk2MDdlOHgxNzYyODk5Nzk5MTc2Mjc4MjU2</UploadId>
//        <Initiator>
//            <ID></ID>
//            <DisplayName></DisplayName>
//        </Initiator>
//        <Owner>
//            <ID></ID>
//            <DisplayName></DisplayName>
//        </Owner>
//        <StorageClass></StorageClass>
//        <Initiated>2025-11-11T22:23:19.183Z</Initiated>
//    </Upload>
//</ListMultipartUploadsResult>
public record ListMultipartUploadsResult(
    String bucket,
    @Nullable String keyMarker,
    @Nullable String uploadIdMarker,
    @Nullable String nextKeyMarker,
    @Nullable String nextUploadIdMarker,
    @Nullable String prefix,
    @Nullable Integer maxUploads,
    boolean isTruncated,
    List<Upload> uploads
) {
    public record Upload(String key, String uploadId, Instant initiated) {}

    public static ListMultipartUploadsResult fromXml(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        var handler = new ListMultipartUploadsResultSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(is, handler);

        return handler.toResult();
    }
}
