package com.simperium.client;

import android.util.Base64;

import org.json.JSONObject;
import org.json.JSONTokener;

// Does NOT use encryption.  Obfuscates only.  This is for testing only!  Proof of concept!
@SuppressWarnings("unused")
public class Base64TestCryptographyAgent extends CryptographyAgent {

    private static final String CIPHERTEXT_PROPERTY_NAME = "cipherText";

    /*
     * Encrypts the rawObject into a string as property "cipherText" of a JSONObject.
     *
     * The only caveat here is that the "application" cannot use the property "cipherText"
     * because it then can't determine if it is already encrypted or not.
     *
     * rawObject {
     *     blah: 'blee'
     * }
     *
     * encryptedObject {
     *     cipherText: 'SDG0ELLEKR3095545405458JG'
     * }
     */
    @Override
    public final JSONObject encryptJson(JSONObject rawObject) {
        String rawText = rawObject.toString();
        JSONObject encryptedObject = new JSONObject();
        try {
            String cipherText;
            if (rawText == null || rawText.length() < 1) {
                cipherText = null;
            } else {
                byte[] bytes = rawText.getBytes();
                cipherText = new String(Base64.encode(bytes, Base64.DEFAULT)); // this should return data that has no quotation marks in it
                cipherText = cipherText.replaceAll("(.{40})", "$1\n"); // LF every 60 chars (if we don't, something else does)
            }
            encryptedObject.put(CIPHERTEXT_PROPERTY_NAME, cipherText);
        } catch (Exception e) {
            throw new RuntimeException("Exception on CryptographyAgent.encryptJson", e);
        }
        return encryptedObject;
    }

    // Decrypts the "cipherText" property of the JSONObject into a new JSONObject.
    @Override
    public final JSONObject decryptJson(JSONObject encryptedObject) {
        if (encryptedObject.has(CIPHERTEXT_PROPERTY_NAME)) {
            String cipherText = encryptedObject.optString(CIPHERTEXT_PROPERTY_NAME, "");
            try {
                String rawText;
                if (cipherText == null || cipherText.length() < 1) {
                    rawText = "";
                } else {
                    cipherText = cipherText.replaceAll("\n", ""); // join lines before decoding
                    byte[] bytes = cipherText.getBytes();
                    rawText = new String(Base64.decode(bytes, Base64.DEFAULT));
                }
                return new JSONObject(new JSONTokener(rawText));
            } catch (Exception e) {
                throw new RuntimeException("Exception on CryptographyAgent.decryptJson", e);
            }
        }
        return encryptedObject;
    }

}

