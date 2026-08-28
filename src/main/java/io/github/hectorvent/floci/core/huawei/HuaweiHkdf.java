package io.github.hectorvent.floci.core.huawei;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Huawei SDK-compatible HKDF-SHA256 derivation for V11-HMAC-SHA256. */
final class HuaweiHkdf {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private HuaweiHkdf() {
    }

    static String deriveHexKey(String accessKey, String secretKey, String scope) {
        byte[] pseudoRandomKey = hmac(
                accessKey.getBytes(StandardCharsets.UTF_8),
                secretKey.getBytes(StandardCharsets.UTF_8));
        byte[] info = scope.getBytes(StandardCharsets.UTF_8);
        byte[] firstBlockInput = new byte[info.length + 1];
        System.arraycopy(info, 0, firstBlockInput, 0, info.length);
        firstBlockInput[info.length] = 0x01;
        return HEX.formatHex(hmac(pseudoRandomKey, firstBlockInput));
    }

    private static byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(value);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
