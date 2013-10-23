package com.simperium.test;

import com.simperium.client.Bucket;
import com.simperium.client.Ghost;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.GhostMissingException;

import java.util.Map;
import java.util.HashMap;

public class MockGhostStore implements GhostStorageProvider {
    private Map<String,Map<String,Ghost>> data = new HashMap<String,Map<String,Ghost>>();
    private Map<String,String> versions = new HashMap<String,String>();
    /**
     * Check if the store has a change version for the provided bucket
     */
    @Override
    public boolean hasChangeVersion(Bucket bucket){
        return versions.containsKey(bucket.getName());
    }

    /**
     * Returns wether the bucket's saved version matches the given version
     */
    @Override
    public boolean hasChangeVersion(Bucket bucket, String version){
        return hasChangeVersion(bucket) && versions.get(bucket.getName()).equals(version);
    }

    /**
     * Returns the current change version for the bucket
     */
    @Override
    public String getChangeVersion(Bucket bucket){
        return versions.get(bucket.getName());
    }

    /**
     * Updates the change version for the given bucket
     */
    @Override
    public void setChangeVersion(Bucket bucket, String version){
        versions.put(bucket.getName(), version);
    }

    /**
     * Checks if there is a ghost for given bucket and key
     */

    @Override
    public boolean hasGhost(Bucket bucket, String key){
        return ghostsForBucket(bucket).containsKey(key);
    }

    /**
     * Builds a ghost from the provided bucket and key
     */
    @Override
    public Ghost getGhost(Bucket bucket, String key) throws GhostMissingException {
        Map<String,Ghost> ghosts = ghostsForBucket(bucket);
        Ghost ghost = ghosts.get(key);
        if (ghost == null) {
            throw(new GhostMissingException());
        }
        return ghost;
    }

    /**
     * Saves the provided ghost to the bucket
     */
    @Override
    public void saveGhost(Bucket bucket, Ghost ghost){
        Map<String,Ghost> ghosts = ghostsForBucket(bucket);
        ghosts.put(ghost.getSimperiumKey(), ghost);
    }

    /**
     * Remove the ghost with the provided key from the store
     */
    @Override
    public void deleteGhost(Bucket bucket, String key){
        Map<String,Ghost> ghosts = ghostsForBucket(bucket);
        ghosts.remove(key);
    }

    /**
     * Clear all ghost data and change version for the given bucket
     */
    @Override
    public void resetBucket(Bucket bucket){
        data.put(bucket.getName(), new HashMap<String,Ghost>());
    }
    
    protected Map<String,Ghost> ghostsForBucket(Bucket bucket){
        String name = bucket.getName();
        Map<String,Ghost> ghosts = data.get(name);
        if (ghosts == null) {
            resetBucket(bucket);
            return ghostsForBucket(bucket);
        }
        return ghosts;
    }
    
}