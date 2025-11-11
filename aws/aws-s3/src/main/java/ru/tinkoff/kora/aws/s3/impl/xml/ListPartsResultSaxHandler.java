package ru.tinkoff.kora.aws.s3.impl.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ListPartsResultSaxHandler extends DefaultHandler {

    private final StringBuilder text = new StringBuilder();

    private String bucket;
    private String key;
    private String uploadId;
    private Integer partNumberMarker;
    private Integer nextPartNumberMarker;
    private Integer maxParts;
    private Boolean isTruncated;
    private final List<ListPartsResult.Part> parts = new ArrayList<>();
    private PartBuilder currentPart;
    private InitiatorBuilder currentInitiator;
    private OwnerBuilder currentOwner;
    private String storageClass;
    private String checksumAlgorithm;
    private String checksumType;

    private static final DateTimeFormatter[] TIME_FORMATS = new DateTimeFormatter[]{
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        DateTimeFormatter.ISO_INSTANT
    };

    private OffsetDateTime parseTime(String v) {
        if (v == null || v.isEmpty()) return null;
        for (var f : TIME_FORMATS) {
            try {return OffsetDateTime.parse(v, f);} catch (Exception ignore) {}
        }
        // Fallback: attempt plain ISO_LOCAL_DATE_TIME as UTC
        try {return OffsetDateTime.parse(v + "Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME);} catch (Exception e) {return null;}
    }

    public ListPartsResult toResult() {
        return new ListPartsResult(
            bucket,
            key,
            uploadId,
            partNumberMarker,
            nextPartNumberMarker,
            maxParts,
            isTruncated,
            List.copyOf(parts),
            currentInitiatorFinal,
            currentOwnerFinal,
            storageClass,
            checksumAlgorithm,
            checksumType
        );
    }

    private ListPartsResult.Initiator currentInitiatorFinal;
    private ListPartsResult.Owner currentOwnerFinal;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        text.setLength(0);
        if ("Part".equals(qName)) {
            currentPart = new PartBuilder();
        } else if ("Initiator".equals(qName)) {
            currentInitiator = new InitiatorBuilder();
        } else if ("Owner".equals(qName)) {
            currentOwner = new OwnerBuilder();
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        text.append(ch, start, length);
    }

    private String t() {
        return text.toString().trim();
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String value = t();

        switch (qName) {
            case "Bucket" -> bucket = value;
            case "Key" -> key = value;
            case "UploadId" -> uploadId = value;
            case "PartNumberMarker" -> partNumberMarker = parseInt(value);
            case "NextPartNumberMarker" -> nextPartNumberMarker = parseInt(value);
            case "MaxParts" -> maxParts = parseInt(value);
            case "IsTruncated" -> isTruncated = parseBool(value);

            // Part fields
            case "ChecksumCRC32" -> {
                if (currentPart != null) currentPart.checksumCRC32 = value;
            }
            case "ChecksumCRC32C" -> {
                if (currentPart != null) currentPart.checksumCRC32C = value;
            }
            case "ChecksumCRC64NVME" -> {
                if (currentPart != null) currentPart.checksumCRC64NVME = value;
            }
            case "ChecksumSHA1" -> {
                if (currentPart != null) currentPart.checksumSHA1 = value;
            }
            case "ChecksumSHA256" -> {
                if (currentPart != null) currentPart.checksumSHA256 = value;
            }
            case "ETag" -> {
                if (currentPart != null) currentPart.eTag = value;
            }
            case "LastModified" -> {
                if (currentPart != null) currentPart.lastModified = parseTime(value);
            }
            case "PartNumber" -> {
                if (currentPart != null) currentPart.partNumber = parseInt(value);
            }
            case "Size" -> {
                if (currentPart != null) currentPart.size = parseLong(value);
            }
            case "Part" -> {
                if (currentPart != null) {
                    parts.add(currentPart.build());
                    currentPart = null;
                }
            }

            // Initiator
            case "DisplayName" -> {
                if (currentInitiator != null && currentOwner == null) {
                    currentInitiator.displayName = value;
                } else if (currentOwner != null) {
                    currentOwner.displayName = value;
                }
            }
            case "ID" -> {
                if (currentInitiator != null && currentOwner == null) {
                    currentInitiator.id = value;
                } else if (currentOwner != null) {
                    currentOwner.id = value;
                }
            }
            case "Initiator" -> {
                if (currentInitiator != null) {
                    currentInitiatorFinal = currentInitiator.build();
                    currentInitiator = null;
                }
            }
            case "Owner" -> {
                if (currentOwner != null) {
                    currentOwnerFinal = currentOwner.build();
                    currentOwner = null;
                }
            }

            case "StorageClass" -> storageClass = value;
            case "ChecksumAlgorithm" -> checksumAlgorithm = value;
            case "ChecksumType" -> checksumType = value;
            default -> { /* ignore unknown */ }
        }

        text.setLength(0);
    }

    private static Integer parseInt(String v) {
        if (v == null || v.isEmpty()) return null;
        try {return Integer.valueOf(v);} catch (NumberFormatException e) {return null;}
    }

    private static Long parseLong(String v) {
        if (v == null || v.isEmpty()) return null;
        try {return Long.valueOf(v);} catch (NumberFormatException e) {return null;}
    }

    private static Boolean parseBool(String v) {
        if (v == null || v.isEmpty()) return null;
        return Boolean.parseBoolean(v);
    }

    // Builders
    private static final class PartBuilder {
        String checksumCRC32;
        String checksumCRC32C;
        String checksumCRC64NVME;
        String checksumSHA1;
        String checksumSHA256;
        String eTag;
        OffsetDateTime lastModified;
        Integer partNumber;
        Long size;

        ListPartsResult.Part build() {
            return new ListPartsResult.Part(
                checksumCRC32,
                checksumCRC32C,
                checksumCRC64NVME,
                checksumSHA1,
                checksumSHA256,
                eTag,
                lastModified,
                partNumber,
                size
            );
        }
    }

    private static final class InitiatorBuilder {
        String displayName;
        String id;

        ListPartsResult.Initiator build() {return new ListPartsResult.Initiator(displayName, id);}
    }

    private static final class OwnerBuilder {
        String displayName;
        String id;

        ListPartsResult.Owner build() {return new ListPartsResult.Owner(displayName, id);}
    }
}
