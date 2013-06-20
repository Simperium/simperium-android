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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simperium.storage.StorageProvider;
import com.simperium.util.Logger;
import com.simperium.util.Uuid;

public class Bucket<T extends Syncable> {
    // The name used for the Simperium namespace
    private String name;
    // User provides the access token for authentication
    private User user;
    // The channel that provides networking and change processing.
    private Channel channel;
    // For storing the bucket listeners
    private Set<Listener<T>> listeners;
    private StorageProvider storageProvider;
    private BucketSchema<T> schema;
    private GhostStore ghostStore;
    private ObjectCache<T> cache;
    /**
     * Represents a Simperium bucket which is a namespace where an app syncs a user's data
     * @param name the name to use for the bucket namespace
     * @param user provides a way to namespace data if a different user logs in
     */
    public Bucket(String name, BucketSchema<T>schema, User user, StorageProvider storageProvider, GhostStore ghostStore){
        this.name = name;
        this.user = user;
        this.storageProvider = storageProvider;
        this.ghostStore = ghostStore;
        this.listeners = Collections.synchronizedSet(new HashSet<Listener<T>>());
        this.schema = schema;
        cache = ObjectCache.buildCache(this);
    }
    /**
     * Bucket.Listener can be leveraged multiple ways if designed correctly
     *  - Channel's can listen for local changes to know when to send changes
     *    to simperium
     *  - Storage mechanisms can listen to all changes (local and remote) so they
     *    can perform their necessary save operations
     */
    public interface Listener<T extends Syncable> {
        public void onObjectRemoved(String key, T object);
        public void onObjectUpdated(String key, T object);
        public void onObjectAdded(String key, T object);
    }

    /**
     * Tell the bucket to sync changes.
     */
    public Change<T> sync(T object){
        if (object.isNew() && !ghostStore.hasGhost(this, object.getSimperiumKey())) {
            storageProvider.addObject(this, object.getSimperiumKey(), object);
        } else {            
            storageProvider.updateObject(this, object.getSimperiumKey(), object);
        }
        
        if (object.isModified())
            return channel.queueLocalChange(object);
        return null;
    }

    /**
     * Tell the bucket to remove the object
     */
    public Change remove(T object){
        cache.remove(object.getSimperiumKey());
        ghostStore.deleteGhost(this, object.getGhost());
        storageProvider.removeObject(this, object.getSimperiumKey());
        Change change = channel.queueLocalDeletion(object);
        Set<Listener<T>> notify;
        synchronized(listeners){
            notify = new HashSet<Listener<T>>(listeners.size());
            notify.addAll(listeners);
        }
        Iterator<Listener<T>> iterator = notify.iterator();
        while(iterator.hasNext()){
            Listener<T> listener = iterator.next();
            try {
                listener.onObjectRemoved(object.getSimperiumKey(),object);
            } catch (Exception e) {
                Logger.log(String.format("Listener failed onObjectRemoved %s", listener));
            }
        }
        return change;
    }
    /**
     * Update the change version and remove the object with the given key
     */
    protected void removeObjectWithKey(String changeVersion, String key){
        removeObjectWithKey(key);
        setChangeVersion(changeVersion);
    }
    /**
     * Given the key for an object in the bucket, remove it if it exists
     */
    protected void removeObjectWithKey(String key){
        try {
            T object = get(key);
            if (object != null) {
                // this will call onObjectRemoved on the listener
                remove(object);
            }
        } catch (BucketObjectMissingException e) {
            // Don't need to do anything, we don't have the object
        }
    }

    /**
     * Add a listener to the bucket. A listener cannot be added more than once.
     */
    public void addListener(Listener<T> listener){
        this.listeners.add(listener);
    }
    /**
     * Remove the listener from the bucket
     */
    public void removeListener(Listener<T> listener){
        this.listeners.remove(listener);
    }
    /**
     * Get the bucket's namespace
     * @return (String) bucket's namespace
     */
    public String getName(){
        return name;
    }

    public String getRemoteName(){
        return schema.getRemoteName();
    }

    public Boolean hasChangeVersion(){
        return ghostStore.hasChangeVersion(this);
    }

    public Boolean hasChangeVersion(String version){
        return ghostStore.hasChangeVersion(this, version);
    }

    public String getChangeVersion(){
        return ghostStore.getChangeVersion(this);
    }

    public void setChangeVersion(String version){
        ghostStore.setChangeVersion(this, version);
    }

    // starts tracking the object
    /**
     * Add an object to the bucket so simperium can start syncing it. Must
     * conform to the Diffable interface. So simperium can diff/apply patches.
     *
     * @param (Diffable) the object to sync
     */
    public void add(T object){
        if (!object.getBucket().equals(this)) {
            object.setBucket(this);
        }
        // TODO: sync the object over the socket
    }

    protected T buildObject(String key, Map<String,java.lang.Object> properties){
        return buildObject(new Ghost(key, 0, properties));
    }


    protected T buildObject(String key){
        return buildObject(key, new HashMap<String,java.lang.Object>());
    }
    
    protected T buildObject(Ghost ghost){
        T object = schema.build(ghost.getSimperiumKey(), Bucket.deepCopy(ghost.getDiffableValue()));
        object.setGhost(ghost);
        object.setBucket(this);
        return object;
    }
    /**
     * Get an object by its key, should we throw an error if the object isn't
     * there?
     */
    public T getObject(String uuid) throws BucketObjectMissingException {
        return get(uuid);
    }
    /**
     * Returns a new objecty tracked by this bucket
     */
    public T newObject(){
        return newObject(uuid());
    }
    /**
     * Returns a new object with the given uuid
     * return null if the uuid exists?
     */
    protected T newObject(String uuid){
        T object = buildObject(uuid, new HashMap<String,java.lang.Object>());
        object.setBucket(this);
        Ghost ghost = new Ghost(uuid, 0, new HashMap<String, java.lang.Object>());
        object.setGhost(ghost);
        ghostStore.saveGhost(this, ghost);
        cache.put(uuid, object);
        return object;
    }
    /**
     * Save the ghost data and update the change version, tell the storage provider
     * that a new object has been added
     */
    protected void addObjectWithGhost(String changeVersion, Ghost ghost){
        setChangeVersion(changeVersion);
        addObjectWithGhost(ghost);
    }
    /**
     * Add object from new ghost data, no corresponding change version so this
     * came from an index request
     */
    protected void addObjectWithGhost(Ghost ghost){
        ghostStore.saveGhost(this, ghost);
        T object = buildObject(ghost);
        Logger.log("Built object with ghost, add it");
        addObject(object);
    }
    /**
     * Update the ghost data
     */
    protected void updateObjectWithGhost(String changeVersion, Ghost ghost){
        setChangeVersion(changeVersion);
        ghostStore.saveGhost(this, ghost);
        T object = buildObject(ghost);
        Logger.log("Built object with ghost, updated it");
        updateObject(object);
    }
    protected void updateGhost(Ghost ghost){
        ghostStore.saveGhost(this, ghost);
        Logger.log("Update the ghost!");
    }
    protected Ghost getGhost(String key) throws GhostMissingException {
        return ghostStore.getGhost(this, key);
    }
    /**
     * Add a new object with corresponding change version
     */
    protected void addObject(String changeVersion, T object){
        addObject(object);
        setChangeVersion(changeVersion);
    }
    /**
     * Adds a new object to the bucket
     */
    protected void addObject(T object){
        object.setGhost(new Ghost(object.getSimperiumKey(), 0, new HashMap<String, java.lang.Object>()));
        
        // Allows the storage provider to persist the object
        Boolean notifyListeners = true;
        if (!object.getBucket().equals(this)) {
            notifyListeners = true;
        }
        object.setBucket(this);
        Logger.log(String.format("Added an object, let's tell the storage provider %s %s", object, object.getGhost()));
        storageProvider.addObject(this, object.getSimperiumKey(), object);
        // notify listeners that an object has been added
        if (notifyListeners) {
            Set<Listener<T>> notify;
            synchronized(listeners){
                notify = new HashSet<Listener<T>>(listeners.size());
                notify.addAll(listeners);
            }

            Iterator<Listener<T>> iterator = notify.iterator();
            while(iterator.hasNext()) {
                Listener<T> listener = iterator.next();
                try {
                    listener.onObjectAdded(object.getSimperiumKey(), object);
                } catch(Exception e) {
                    Logger.log(String.format("Listener failed onObjectAdded %s", listener), e);
                }
            }
        }
    }
    /**
     * Updates an existing object
     */
    protected void updateObject(T object){
        object.setBucket(this);
        storageProvider.updateObject(this, object.getSimperiumKey(), object);
        Set<Listener<T>> notify;
        synchronized(listeners){
            notify = new HashSet<Listener<T>>(listeners.size());
            notify.addAll(listeners);
        }
        Iterator<Listener<T>> iterator = notify.iterator();
        while(iterator.hasNext()) {
            Listener<T> listener = iterator.next();
            try {
                listener.onObjectUpdated(object.getSimperiumKey(), object);
            } catch(Exception e) {
                Logger.log(String.format("Listener failed onObjectUpdated %s", listener));
            }
        }
    }
    /**
     *
     */
    protected void updateObject(String changeVersion, T object){
        updateObject(object);
        setChangeVersion(changeVersion);
    }
    // TODO: remove the channel getter/setter, Channel will use a listening
    // interface
    public void setChannel(Channel channel){
        this.channel = channel;
    }

    public Channel getChannel(){
        return channel;
    }

    /**
     * Tell the bucket that an object has local changes ready to sync.
     * @param (Diffable) object
     */
    public void update(T object){
    }
    /**
     * Initialize the bucket to start tracking changes. We can provide a way
     * for storage mechanisms to initialize here.
     */
    public void start(){
        channel.start();
    }
    /*
     * Reset bucket what is on the server
     */
    public void reset(){
        storageProvider.resetBucket(this);
        ghostStore.resetBucket(this);
        channel.reset();
        // Clear the ghost store
    }
    /**
     * Get a single object object that matches key
     */
    public T get(String key) throws BucketObjectMissingException {
        // If the cache has it, return the cached object
        T object = cache.get(key);
        if (object != null) {
            return object;
        }
        // Datastore constructs the object for us
        Ghost ghost = null;
        try {
            ghost = ghostStore.getGhost(this, key);            
        } catch (GhostMissingException e) {
            throw(new BucketObjectMissingException(String.format("Bucket %s does not have object %s", getName(), key)));
        }
        Map<String,java.lang.Object> properties = storageProvider.getObject(this, key);
        object = schema.build(key, properties);
        Logger.log(String.format("Fetched ghost for %s %s", key, ghost));
        object.setBucket(this);
        object.setGhost(ghost);
        cache.put(key, object);
        return object;
    }
    /**
     * Does bucket have at least the requested version?
     */
    public Boolean containsKey(String key){
        return ghostStore.hasGhost(this, key);
    }
    /**
     * Ask storage if it has at least the requested version or newer
     */
    public Boolean hasKeyVersion(String key, Integer version){
        try {
            Ghost ghost = ghostStore.getGhost(this, key);
            return ghost.getVersion().equals(version);
        } catch (GhostMissingException e) {
            // we don't have the ghost
            return false;
        }
    }
    /**
     * Which version of the key do we have
     */
    public Integer getKeyVersion(String key) throws GhostMissingException {
        Ghost ghost = ghostStore.getGhost(this, key);
        return ghost.getVersion();
    }

    public String uuid(){
        String key;
        do {
            key = Uuid.uuid();
        } while(containsKey(key));
        return key;
    }

    /**
     * Copy a hash
     */
    public static Map<String, java.lang.Object> deepCopy(Map<String, java.lang.Object> map){
        if (map == null) {
            return null;
        };
        Map<String,java.lang.Object> copy = new HashMap<String,java.lang.Object>(map.size());
        Iterator keys = map.keySet().iterator();
        while(keys.hasNext()){
            String key = (String)keys.next();
            java.lang.Object val = map.get(key);
            // Logger.log(String.format("Hello! %s", json.get(key).getClass().getName()));
            if (val instanceof Map) {
                copy.put(key, deepCopy((Map<String,java.lang.Object>) val));
            } else if (val instanceof List) {
                copy.put(key, deepCopy((List<java.lang.Object>) val));
            } else {
                copy.put(key, val);
            }
        }
        return copy;
    }
    /**
     * Copy a list
     */
    public static List<java.lang.Object>deepCopy(List<java.lang.Object> list){
        if (list == null) {
             return null;
        };
        List<java.lang.Object> copy = new ArrayList<java.lang.Object>(list.size());
        for (int i=0; i<list.size(); i++) {
            java.lang.Object val = list.get(i);
            if (val instanceof Map) {
                copy.add(deepCopy((Map<String,java.lang.Object>) val));
            } else if (val instanceof List) {
                copy.add(deepCopy((List<java.lang.Object>) val));
            } else {
                copy.add(val);
            }
        }
        return copy;
    }

}
