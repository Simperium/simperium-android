package com.simperium.client;

import com.simperium.util.Logger;

import java.util.Map;

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
            Logger.log("Simperium.Syncable", String.format("Setting ghost %s %s", getSimperiumKey(), ghost));
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
        return !getDiffableValue().equals(ghost.getDiffableValue());
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
    public Map<String, Object>getUnmodifiedValue(){
        return getGhost().getDiffableValue();
    }

    /**
     * Send modifications over the socket to simperium
     */
    public Change save(){
        return getBucket().sync(this);
    }

    /**
     * Sends a delete operation over the socket
     */
    public Change delete(){
        return getBucket().remove(this);
    }

    /**
     * Get this object's revisions
     */
    public ChannelProvider.RevisionsRequest getRevisions(Bucket.RevisionsRequestCallbacks<? extends Syncable> callbacks){
        return getBucket().getRevisions(this, callbacks);
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

}