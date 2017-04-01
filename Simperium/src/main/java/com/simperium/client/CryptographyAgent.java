package com.simperium.client;

import org.json.JSONObject;

// todo review exception handling

/* todo delete this after discussing with Simperium devs:
    Tried just using getDiffableValue to encrypt, but was a problem since it is used in so many places where we may or may not want data to already be encrypted.  What we want to do is...
    A) When we send a change, we need to send a delta between the ENCRYPTED versions of the note (before and after change).
        When to sync up the raw version and the encrypted version?
    B) When we received a change, we need to apply the delta between the ENCRYPTED versions of the note (before/after change).
        Then, we need to update the ENCRYPTED and DEcrypted versions of the data.
    C) When we send the whole thing over the wire (d), we need to send the ENCRYPTED version of the note (after).
        When to sync up the raw version and the encrypted version?
    D) When we receive the whole thing over the wire (d) or (e), we need to receive the ENCRYPTED version of the note and decrypt before inserting into the ghost.
        Then, we need to update the encrypted and unencrypted versions of the data.
    Need to retain copies of both the encrypted and decrypted notes (for the ghost).
 */

/*
 * The application should extend this agent with the real implementation
 * and set the instance at startup, like:
 *   CryptographyAgent.setInstance(new Base64TestCryptographyAgent());
 * The extension should know how to encrypt and decrypt JSONOjects.
 */
public abstract class CryptographyAgent {

    private static CryptographyAgent CRYPTOGRAPHY_AGENT_INSTANCE;

    @SuppressWarnings("unused")
    public static void setInstance(CryptographyAgent cryptographyAgent) {
        CRYPTOGRAPHY_AGENT_INSTANCE = cryptographyAgent;
    }

    static CryptographyAgent getInstance() {
        return CRYPTOGRAPHY_AGENT_INSTANCE;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled() {
        return CRYPTOGRAPHY_AGENT_INSTANCE != null;
    }

    // The actual implementation of the encryption depends on the application and the encryption mechanism.
    // En/Decrypting in the client prevents property name collisions between encrypted and unencrypted BucketObjects.
    public abstract JSONObject encryptJson(JSONObject rawObject);

    public abstract JSONObject decryptJson(JSONObject encryptedObject);
}
