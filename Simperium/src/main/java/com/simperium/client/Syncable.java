package com.simperium.client;

import org.json.JSONObject;

import com.simperium.util.JSONDiff;

/**
 * An object that can be diffed and changes sent
 */
public abstract class Syncable implements Diffable {
    private Ghost mGhost;
    protected Bucket mBucket;

    public Integer getVersion() {
        return mGhost.getVersion();
    }

    protected Ghost getGhost() {
        return mGhost;
    }

    protected void setGhost(Ghost ghost) {
        synchronized(this) {
            mGhost = ghost;
        }
    }

    /**
     * Has this ever been synced
     */
    public Boolean isNew() {
        return getVersion() == null || getVersion() == 0;
    }

    /**
     * Does the local object have modifications?
     */
    public Boolean isModified() {

        // Protected against Concurrent modification exception: see #158
        JSONObject value = getDiffableValue();

        synchronized (value) {
            return !JSONDiff.equals(value, mGhost.getDiffableValue());
        }
    }

    public String getBucketName() {
        if (mBucket != null) {
            return mBucket.getName();
        }
        return null;
    }

    public Bucket getBucket() {
        return mBucket;
    }

    public void setBucket(Bucket bucket) {
        mBucket = bucket;
    }

    /**
     * Returns the object as it should appear on the server
     */
    public JSONObject getUnmodifiedValue() {
        return getGhost().getEncryptedValue();
    }

    /**
     * Send modifications over the socket to simperium
     */
    public void save() {
        getBucket().sync(this);
    }

    /**
     * Sends a delete operation over the socket
     */
    public void delete() {
        getBucket().remove(this);
    }

    /**
     * Key.VersionId
     */
    public String getVersionId() {
        if (getGhost() == null) {
            return String.format("%s.?", getSimperiumKey());
        }
        return getGhost().getVersionId();
    }

    public void notifySaved() {
        if (mBucket != null) {
            mBucket.notifyOnSaveListeners(this);
        }
    }

    @Override
    public String toString() {
        return "<Syncable " + getBucketName()  + "." + getSimperiumKey() + ">";
    }
}