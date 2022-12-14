package com.ae.utils;


import java.security.Key;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import Decoder.BASE64Encoder;
import Decoder.BASE64Decoder;

public class AES {
    private static final String BASETYPE = "AES/ECB/PKCS5Padding";

    public static String Encrypt(String keyStr, String plainText) {
        byte[] encrypt = null;
        try {
            Key key = generateKey(keyStr);
            Cipher cipher = Cipher.getInstance(BASETYPE);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            encrypt = cipher.doFinal(plainText.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new BASE64Encoder().encode(encrypt);
    }

    public static String Decrypt(String keyStr, String encryptData) {
        byte[] decrypt = null;
        try {
            Key key = generateKey(keyStr);
            Cipher cipher = Cipher.getInstance(BASETYPE);
            cipher.init(Cipher.DECRYPT_MODE, key);
            decrypt = cipher.doFinal(new BASE64Decoder().decodeBuffer(encryptData));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return new String(decrypt);
    }

    private static Key generateKey(String key) throws Exception {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
            return keySpec;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

    }


    //加密
    public static String encode(String str,String key) {

        return Encrypt(key, str.replace("\n", "x0xnby0")).replace("\n", "x0xnby1").replace("\r", "x0xnby2");
    }

    //解密
    public static String decode(String str,String key) {
        return Decrypt(key, str.replace("x0xnby2", "\r").replace("x0xnby1", "\n")).replace("x0xnby0", "\n");
    }


}
