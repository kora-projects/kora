package ru.tinkoff.kora.aws.s3.model.response;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;

/*

   <Name>string</Name>
   <Prefix>string</Prefix>
   <Delimiter>string</Delimiter>
   <MaxKeys>integer</MaxKeys>
   <CommonPrefixes>
      <Prefix>string</Prefix>
   </CommonPrefixes>
   ...
   <EncodingType>string</EncodingType>
   <KeyCount>integer</KeyCount>
   <ContinuationToken>string</ContinuationToken>
   <NextContinuationToken>string</NextContinuationToken>
   <StartAfter>string</StartAfter>
 */
public record ListBucketResult(@Nullable List<String> commonPrefixes, int keyCount, @Nullable String nextContinuationToken, List<ListBucketItem> items) {
    /*
     <Contents>
      <ChecksumAlgorithm>string</ChecksumAlgorithm>
      ...
      <ChecksumType>string</ChecksumType>
      <ETag>string</ETag>
      <Key>string</Key>
      <LastModified>timestamp</LastModified>
      <Owner>
         <DisplayName>string</DisplayName>
         <ID>string</ID>
      </Owner>
      <RestoreStatus>
         <IsRestoreInProgress>boolean</IsRestoreInProgress>
         <RestoreExpiryDate>timestamp</RestoreExpiryDate>
      </RestoreStatus>
      <Size>long</Size>
      <StorageClass>string</StorageClass>
   </Contents>
     */

    public record ListBucketItem(
        String bucket, String key, String etag, String checksumType, String checksumAlgorithm, Instant lastModified, long size, @Nullable String storageClass, @Nullable ListBucketItemOwner owner
    ) {}

    public record ListBucketItemOwner(String displayName, String id) {}
}
