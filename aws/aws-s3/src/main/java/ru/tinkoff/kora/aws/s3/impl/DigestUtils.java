package ru.tinkoff.kora.aws.s3.impl;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

public class DigestUtils {

    public static MessageDigest md5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Digest(byte[] digest) {
        public String hex() {
            return HexFormat.of().formatHex(digest);
        }

        public String base64() {
            return Base64.getEncoder().encodeToString(digest);
        }
    }

    public static Digest sha256(byte[] data, int offset, int length) {
        var digest = sha256();
        digest.update(data, offset, length);
        return new Digest(digest.digest());
    }

    public static Digest md5(byte[] data, int off, int len) {
        var md5 = md5();
        md5.update(data, off, len);
        return new Digest(md5.digest());
    }

    public static Digest sha256(String str) {
        var bytes = str.getBytes(StandardCharsets.UTF_8);
        return sha256(bytes, 0, bytes.length);
    }

    public static byte[] sumHmac(byte[] key, byte[] data) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(data);
            return mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e); // never gonna happen
        }
    }

    public static byte[] sumHmac(Mac key, byte[] data) {
        try {
            var mac = (Mac) key.clone();
            mac.update(data);
            return mac.doFinal();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e); // never gonna happen
        }
    }
}
