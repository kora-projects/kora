package ru.tinkoff.kora.aws.s3.impl.xml;

/*
<ListPartsResult>
   <Bucket>string</Bucket>
   <Key>string</Key>
   <UploadId>string</UploadId>
   <PartNumberMarker>integer</PartNumberMarker>
   <NextPartNumberMarker>integer</NextPartNumberMarker>
   <MaxParts>integer</MaxParts>
   <IsTruncated>boolean</IsTruncated>
   <Part>
      <ChecksumCRC32>string</ChecksumCRC32>
      <ChecksumCRC32C>string</ChecksumCRC32C>
      <ChecksumCRC64NVME>string</ChecksumCRC64NVME>
      <ChecksumSHA1>string</ChecksumSHA1>
      <ChecksumSHA256>string</ChecksumSHA256>
      <ETag>string</ETag>
      <LastModified>timestamp</LastModified>
      <PartNumber>integer</PartNumber>
      <Size>long</Size>
   </Part>
   ...
   <Initiator>
      <DisplayName>string</DisplayName>
      <ID>string</ID>
   </Initiator>
   <Owner>
      <DisplayName>string</DisplayName>
      <ID>string</ID>
   </Owner>
   <StorageClass>string</StorageClass>
   <ChecksumAlgorithm>string</ChecksumAlgorithm>
   <ChecksumType>string</ChecksumType>
</ListPartsResult>
 */

import jakarta.annotation.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;

public record ListPartsResult(
    String bucket,
    String key,
    String uploadId,
    Integer partNumberMarker,
    Integer nextPartNumberMarker,
    Integer maxParts,
    boolean isTruncated,
    List<Part> parts,
    Initiator initiator,
    Owner owner,
    String storageClass,
    String checksumAlgorithm,
    String checksumType
) {
    public record Part(
        @Nullable
        String checksumCRC32,
        @Nullable
        String checksumCRC32C,
        @Nullable
        String checksumCRC64NVME,
        @Nullable
        String checksumSHA1,
        @Nullable
        String checksumSHA256,
        String eTag,
        OffsetDateTime lastModified,
        int partNumber,
        long size
    ) {}

    public record Initiator(
        String displayName,
        String id
    ) {}

    public record Owner(
        String displayName,
        String id
    ) {}

    public static ListPartsResult fromXml(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        var handler = new ListPartsResultSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(is, handler);

        return handler.toResult();
    }

}
