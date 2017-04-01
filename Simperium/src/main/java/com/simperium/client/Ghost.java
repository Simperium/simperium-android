package com.simperium.client;

import org.json.JSONObject;

import java.util.Locale;

public class Ghost implements Diffable {

    static public final String TAG = "Simperium";

    private String mKey;
    private Integer mVersion = 0;
    private JSONObject mProperties; // note: encrypted; do not create a setter for this without handling the decrypted version, too (see constructor)
    private final JSONObject mDecryptedProperties;

    public Ghost(String key) {
        this(key, 0, new JSONObject());
    }

    public Ghost(String key, Integer version, JSONObject properties) {
        super();
        mKey = key;
        mVersion = version;
        // copy the properties
        mProperties = properties;
        mDecryptedProperties = CryptographyAgent.isEnabled() ? CryptographyAgent.getInstance().decryptJson(mProperties) : mProperties;
    }

    public String getSimperiumKey() {
        return mKey;
    }
    public Integer getVersion() {
        return mVersion;
    }

    public JSONObject getDiffableValue() {
        return mDecryptedProperties;
    }

    public JSONObject getEncryptedValue() {
        return mProperties;
    }

    public String getVersionId() {
        return String.format(Locale.US, "%s.%d", mKey, mVersion);
    }

    public String toString() {
        return String.format(Locale.US, "Ghost %s", getVersionId());
    }
}
