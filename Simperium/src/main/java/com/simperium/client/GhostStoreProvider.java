package com.simperium.client;

public interface GhostStoreProvider {
    /**
     * Check if the store has a change version for the provided bucket
     */
    public boolean hasChangeVersion(Bucket bucket);
    /**
     * Returns wether the bucket's saved version matches the given version
     */
    public boolean hasChangeVersion(Bucket bucket, String version);
    /**
     * Returns the current change version for the bucket
     */
    public String getChangeVersion(Bucket bucket);
    /**
     * Updates the change version for the given bucket
     */
    public void setChangeVersion(Bucket bucket, String version);
    /**
     * Checks if there is a ghost for given bucket and key
     */
    public boolean hasGhost(Bucket bucket, String key);
    /**
     * Builds a ghost from the provided bucket and key
     */
    public Ghost getGhost(Bucket bucket, String key) throws GhostMissingException;

    /**
     * Get the currently stored version number for the key in the bucket
     */
    public int getGhostVersion(Bucket bucket, String key) throws GhostMissingException;
    /**
     * Saves the provided ghost to the bucket
     */
    public void saveGhost(Bucket bucket, Ghost ghost);
    /**
     * Remove the ghost with the provided key from the store
     */
    public void deleteGhost(Bucket bucket, String key);
    /**
     * Clear all ghost data and change version for the given bucket
     */
    public void resetBucket(Bucket bucket);
}

