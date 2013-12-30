package com.simperium.client;

import org.json.JSONObject;

import com.simperium.util.JSONDiff;

/**
 * An object that can be diffed and changes sent
 */
public abstract class Syncable implements Diffable {
    private Ghost ghost;
    protected Bucket bucket;

    public Integer getVersion(){
        return ghost.getVersion();
    }

    protected Ghost getGhost(){
        return ghost;
    }

    protected void setGhost(Ghost ghost){
        synchronized(this){
            this.ghost = ghost;
        }
    }

    /**
     * Has this ever been synced
     */
    public Boolean isNew(){
        return getVersion() == null || getVersion() == 0;
    }

    /**
     * Does the local object have modifications?
     */
    public Boolean isModified(){
        return !JSONDiff.equals(getDiffableValue(), ghost.getDiffableValue());
    }

    public String getBucketName(){
        if (bucket != null) {
            return bucket.getName();
        }
        return null;
    }

    public Bucket getBucket(){
        return bucket;
    }

    public void setBucket(Bucket bucket){
        this.bucket = bucket;
    }

    /**
     * Returns the object as it should appear on the server
     */
    public JSONObject getUnmodifiedValue(){
        return getGhost().getDiffableValue();
    }

    /**
     * Send modifications over the socket to simperium
     */
    public void save(){
        getBucket().sync(this);
    }

    /**
     * Sends a delete operation over the socket
     */
    public void delete(){
        getBucket().remove(this);
    }

    /**
     * Key.VersionId
     */
    public String getVersionId(){
        if (getGhost() == null) {
            return String.format("%s.?", getSimperiumKey());
        }
        return getGhost().getVersionId();
    }

    public void notifySaved(){
        if (bucket != null) {
            bucket.notifyOnSaveListeners(this);
        }
    }
}