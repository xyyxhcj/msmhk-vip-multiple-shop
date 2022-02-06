package vip.msmhk.util;

import org.springframework.util.Base64Utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author xyyxhcj@qq.com
 * @since 2021/06/29
 */

public class AesUtils {
    private final static String TRANSFORMATION = "AES";
    private final static String AES = "AES";

    public static String encrypt(String input, String key) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), AES);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] bytes = cipher.doFinal(input.getBytes());
        return Base64Utils.encodeToString(bytes);
    }

    public static String decrypt(String input, String key) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), AES);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        byte[] output = cipher.doFinal(Base64Utils.decodeFromString(input));
        return new String(output);
    }
}
