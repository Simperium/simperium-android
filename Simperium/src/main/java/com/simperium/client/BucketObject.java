package com.simperium.client;

import com.simperium.util.JSONDiff;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A generic object used to represent a single object from a bucket
 */
public class BucketObject extends Syncable {

    static public final String TAG = "Simperium";

    /**
     * Basic implementation of BucketSchema for BucketObject
     */
    public static class Schema extends BucketSchema<BucketObject>{
        private String mRemoteName;

        public Schema() {
            this(null);
        }

        public Schema(String name) {
            mRemoteName = name;
        }

        public String getRemoteName() {
            return mRemoteName;
        }

        public String getRemoteName(Bucket bucket) {
            if (mRemoteName == null) {
                return bucket.getName();
            }
            return mRemoteName;
        }

        public BucketObject build(String key, JSONObject properties) {
            return new BucketObject(key, properties);
        }

        public void update(BucketObject object, JSONObject properties) {
            object.mProperties = properties;
        }

    }

    private String mSimperiumKey;
    protected JSONObject mProperties;

    public BucketObject(String key, JSONObject properties) {
        mSimperiumKey = key;
        mProperties = JSONDiff.deepCopy(properties);
    }

    public BucketObject(String key) {
        this(key, new JSONObject());
    }

    public JSONObject getProperties() {
        return mProperties;
    }

    public void setProperties(JSONObject properties) {
        mProperties = properties;
    }

    public Object getProperty(String key) {
        return mProperties.opt(key);
    }

    public void setProperty(String key, Object value) {
        try {
            mProperties.put(key, value);
        } catch (JSONException e) {
            android.util.Log.e(TAG, "Could not set key" + key, e);
        }
    }

    public String getSimperiumKey() {
        return mSimperiumKey;
    }

    public String toString() {
        if (getBucket() == null) {
            return String.format("<no bucket> - %s", getVersionId());
        }
        return String.format("%s - %s", getBucket().getName(), getVersionId());
    }

    public JSONObject getDiffableValue() {
        if (mProperties == null) {
            mProperties = new JSONObject();
        }
        return mProperties;
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if( o == this) {
            return true;
        } else if(o.getClass() != getClass()) {
            return false;
        }
        BucketObject other = (BucketObject) o;
        return other.getBucket().equals(getBucket()) && other.getSimperiumKey().equals(getSimperiumKey());
    }

}
