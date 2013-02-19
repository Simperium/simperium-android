/**
 * For storing and syncing entities with Simperium. To create a bucket you use
 * the Simperium.bucket instance method:
 *    
 *    // Initialize simperium
 *    Simperium simperium = new Simperium(...);
 *    Bucket notesBucket = simperium.bucket("notes", Note.class);
 *
 * Simperium creates a Bucket instance that is backed by a Channel. The Channel
 * takes care of the network operations by communicating with the WebSocketManager.
 *
 * TODO: A bucket should be able to be queried: "give me all your entities". This
 * potentially needs to be flexible to allow storage mechanisms way to extend how
 * things can be queried.
 *
 * Buckets should also provide a way for other objects to listen for when entities
 * get added, updated or removed due to operations coming in from the network.
 *
 * A bucket should also provide an interface that can listen to local changes so
 * that the channel can see when entities are changed on the client and push them
 * out to Simperium.
 *
 */

package com.simperium.client;

import java.util.Vector;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.util.Map;

public class Bucket {
    /**
     * Bucket.Listener can be leveraged multiple ways if designed correctly
     *  - Channel's can listen for local changes to know when to send changes
     *    to simperium
     *  - Storage mechanisms can listen to all changes (local and remote) so they
     *    can perform their necessary save operations
     */
    public interface Listener {
        void onEntityUpdated(String key, Integer version);
        void onEntityRemoved(String key);
        void onEntityAdded(String key);
    }

    /**
     * The interface all objects must conform to in order to be able to be
     * tracked by Simperium. For a default implementation see BucketObject
     */
    public interface Diffable {
        void setBucket(Bucket bucket);
        Bucket getBucket();
        void setSimperiumId(String id);
        String getSimperiumId();
        void setVersion(Integer version);
        Integer getVersion();
        Map<String,Object> getDiffableValue();
    }
    // The name used for the Simperium namespace
    private String name;
    // User provides the access token for authentication
    private User user;
    // The bucketType class will provide the mechanism for turning JSON data into proper
    // java instances.
    private Class<? extends Diffable>bucketType;
    // The channel that provides networking and change processing. This may be removed
    // and functionality will be provided via a listener interface of some kind
    // TODO: provide an interface for a class to observe when local changes are made
    // this is how the Channel associated with the bucket will determine if there are
    // local changes pending
    private Channel channel;
    // For storing the bucket listeners
    private Vector<Listener> listeners = new Vector<Listener>();
    private StorageProvider storageProvider;
    /**
     * Represents a Simperium bucket which is a namespace where an app syncs a user's data
     * @param name the name to use for the bucket namespace
     * @param bucketType the class that is used to construct java objects from data
     * @param user provides a way to namespace data if a different user logs in
     */
    public Bucket(String name, Class<? extends Diffable>bucketType, User user, StorageProvider storageProvider){
        this.name = name;
        this.user = user;
        this.bucketType = bucketType;
        this.storageProvider = storageProvider;
        this.listeners = new Vector<Listener>();
    }
    /**
     * 
     */
    public void addListener(Listener listener){
        this.listeners.add(listener);
    }
    public void removeListener(Listener listener){
        this.listeners.remove(listener);
    }
    /**
     * Get the bucket's namespace
     * @return (String) bucket's namespace
     */
    public String getName(){
        return name;
    }
    
    public Boolean hasChangeVersion(){
        return storageProvider.hasChangeVersion(this);
    }
    
    public Boolean hasChangeVersion(String version){
        return storageProvider.hasChangeVersion(this, version);
    }
    
    public String getChangeVersion(){
        return storageProvider.getChangeVersion(this);
    }
    
    public void setChangeVersion(String version){
        storageProvider.setChangeVersion(this, version);
    }
    
    // starts tracking the object
    /**
     * Add an object to the bucket so simperium can start syncing it. Must
     * conform to the Diffable interface. So simperium can diff/apply patches.
     *
     * @param (Diffable) object the entity to sync
     */
    public void add(Diffable object){
        if (!object.getBucket().equals(this)) {
            object.setBucket(this);
        }
        // TODO: sync the object over the socket
    }
    /**
     * Adds a new entity to the bucket
     */
    protected void addEntity(Entity entity){
        // Allows the storage provider to persist the entity
        storageProvider.addEntity(this, entity.getSimperiumId(), entity);
        // notify listeners that an entity has been added
        Vector<Listener> notify;
        synchronized(listeners){
            notify = new Vector<Listener>(listeners.size());
            notify.addAll(listeners);
        }
        Simperium.log(String.format("Notifying %d listeners", notify.size()));
        
        Iterator<Listener> iterator = notify.iterator();
        while(iterator.hasNext()) {
            Listener listener = iterator.next();
            try {
                listener.onEntityAdded(entity.getSimperiumId());                
            } catch(Exception e) {
                Simperium.log(String.format("Listener failed onEntityAdded %s", listener));
            }
        }
    }
    /**
     * Updates an existing entity
     */
    protected void updateEntity(Entity entity){
        storageProvider.updateEntity(this, entity.getSimperiumId(), entity);
        Vector<Listener> notify;
        synchronized(listeners){
            notify = new Vector<Listener>(listeners.size());
            notify.addAll(listeners);
        }
        Simperium.log(String.format("Notifying %d listeners", notify.size()));
        
        Iterator<Listener> iterator = notify.iterator();
        while(iterator.hasNext()) {
            Listener listener = iterator.next();
            try {
                listener.onEntityUpdated(entity.getSimperiumId(), entity.getVersion());                
            } catch(Exception e) {
                Simperium.log(String.format("Listener failed onEntityUpdated %s", listener));
            }
        }
    }
    // TODO: remove the channel getter/setter, Channel will use a listening
    // interface
    protected void setChannel(Channel channel){
        this.channel = channel;
    }
    
    protected Channel getChannel(){
        return channel;
    }
    
    /**
     * Tell the bucket that an object has local changes ready to sync.
     * @param (Diffable) object
     */
    public void update(Diffable object){
    }
    /**
     * Initialize the bucket to start tracking changes. We can provide a way
     * for storage mechanisms to initialize here.
     */
    public void start(){
        channel.start();
    }
    /**
     * Get a single object entity that matches key
     */
    public Entity get(String key){
        // TODO: ask the datastore to find the object
        return storageProvider.getEntity(this, key);
    }
    /**
     * 
     */
    public void put(String key, Diffable object){
        // storageProvider.putEntity(this, key, object);
    }
    /**
     * Get a list of objects based on the provided keys
     */
    public List<Diffable> getAll(String[] ... keys){
        return null;
    }
    /**
     * Does bucket have at least the requested version?
     */
    public Boolean containsKey(String key){
        return storageProvider.containsKey(this, key);
    }
    /**
     * Ask storage if it has at least the requested version or newer
     */
    public Boolean hasKeyVersion(String key, Integer version){
        return storageProvider.hasKeyVersion(this, key, version);
    }
    /**
     * Which version of the key do we have
     */
    public Integer getKeyVersion(String key){
        return storageProvider.getKeyVersion(this, key);
    }

}