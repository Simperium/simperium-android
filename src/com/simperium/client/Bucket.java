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

public class Bucket<T extends Bucket.Syncable> {
    // The name used for the Simperium namespace
    private String name;
    // User provides the access token for authentication
    private User user;
    // The channel that provides networking and change processing.
    private Channel channel;
    // For storing the bucket listeners
    private Set<Listener<T>> listeners;
    private StorageProvider storageProvider;
    private Schema<T> schema;
    private GhostStore ghostStore;
    /**
     * Represents a Simperium bucket which is a namespace where an app syncs a user's data
     * @param name the name to use for the bucket namespace
     * @param user provides a way to namespace data if a different user logs in
     */
    protected Bucket(String name, Schema<T>schema, User user, StorageProvider storageProvider, GhostStore ghostStore){
        this.name = name;
        this.user = user;
        this.storageProvider = storageProvider;
        this.ghostStore = ghostStore;
        this.listeners = Collections.synchronizedSet(new HashSet<Listener<T>>());
        this.schema = schema;
    }
    /**
     * Bucket.Listener can be leveraged multiple ways if designed correctly
     *  - Channel's can listen for local changes to know when to send changes
     *    to simperium
     *  - Storage mechanisms can listen to all changes (local and remote) so they
     *    can perform their necessary save operations
     */
    public static interface Listener<T extends Bucket.Syncable> {
        public void onObjectRemoved(String key, T object);
        public void onObjectUpdated(String key, T object);
        public void onObjectAdded(String key, T object);
    }
    /**
     * The interface all objects must conform to in order to be able to be
     * tracked by Simperium. For a default implementation see BucketObject
     */
    public interface Diffable {
        // void setSimperiumId(String id);
        String getSimperiumKey();
        // void setVersion(Integer version);
        Integer getVersion();
        Map<String,java.lang.Object> getDiffableValue();
    }
    /**
     *
     */
    protected static class Ghost implements Diffable {
        private String key;
        private Integer version;
        private Map<String, java.lang.Object> properties;
        public Ghost(String key, Integer version, Map<String, java.lang.Object> properties){
            this.key = key;
            this.version = version;
            // copy the properties
            this.properties = Bucket.deepCopy(properties);
        }
        public String getSimperiumKey(){
            return key;
        }
        public Integer getVersion(){
            return version;
        }
        public Map<String, java.lang.Object> getDiffableValue(){
            return properties;
        }
    }
    /**
     * An object that can be diffed and changes sent
     */
    public static abstract class Syncable implements Diffable {
        private Diffable ghost;

        public abstract void setBucket(Bucket bucket);
        public abstract Bucket getBucket();
        public abstract String bucketName();
        public abstract Boolean isNew();
        protected Diffable getGhost(){
            return ghost;
        }
        public Integer getVersion(){
            return this.ghost.getVersion();
        }
        private void setGhost(Diffable ghost){
            this.ghost = ghost;
        }
        /**
         * Does the local object have modifications?
         */
        public Boolean isModified(){
            return !getDiffableValue().equals(ghost.getDiffableValue());
        }
        /**
         * Returns the object as it should appear on the server
         */
        public Map<String, java.lang.Object>getUnmodifiedValue(){
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
    }
    /**
     * An interface to allow applications to provide a schema for a bucket and a way
     * instatiate custom Bucket.Object instances
     */
    public static abstract class Schema<T extends Bucket.Syncable> {
        public String getRemoteName(Bucket bucket) {
            return bucket.getName();
        }
        public abstract T build(String key, Map<String,java.lang.Object>properties);
    }
    /**
     * Basic implementation of Bucket.Schema for Bucket.Object
     */
    public static class ObjectSchema extends Schema<Object>{
        public String getRemoteName(Bucket bucket){
            return bucket.getName();
        }
        public Object build(String key, Map<String,java.lang.Object> properties){
            return new Object(key, properties);
        }
    }
    /**
     * A generic object used to represent a single object from a bucket
     */
    public static class Object extends Syncable {

        private Bucket bucket;
        private String simperiumKey;

        protected Map<String,java.lang.Object> properties;

        public Object(String key, Map<String,java.lang.Object> properties){
            this.simperiumKey = key;
            this.properties = Bucket.deepCopy(properties);
        }

        public Object(String key){
            this(key, new HashMap<String, java.lang.Object>());
        }

        public Bucket getBucket(){
            return bucket;
        }

        public void setBucket(Bucket bucket){
            this.bucket = bucket;
        }

        public String getSimperiumKey(){
            return simperiumKey;
        }

        public Integer getVersion(){
			Simperium.log(String.format("t %s", getGhost()));
            return getGhost().getVersion();
        }

        public Boolean isNew(){
			Simperium.log(String.format("g %s", getGhost()));
            return getGhost().getVersion() == null || getGhost().getVersion() == 0;
        }

        public String bucketName(){
            Bucket bucket = getBucket();
            if (bucket != null) {
                return bucket.getName();
            }
            return null;
        }

        public String toString(){
            return String.format("%s - %s", getBucket().getName(), getSimperiumKey());
        }

        public Map<String,java.lang.Object> getDiffableValue(){
            return properties;
        }

        public boolean equals(java.lang.Object o){
            if (o == null) {
                return false;
            } else if( o == this){
                return true;
            } else if(o.getClass() != getClass()){
                return false;
            }
            Bucket.Object other = (Bucket.Object) o;
            return other.getBucket().equals(getBucket()) && other.getSimperiumKey().equals(getSimperiumKey());
        }
    }

    /**
     * Tell the bucket to sync changes.
     */
    public void sync(T object){
    	if (object.isNew() && !ghostStore.hasGhost(this, object.getSimperiumKey())) {
    		storageProvider.addObject(this, object.getSimperiumKey(), object);
    	} else {    		
    		storageProvider.updateObject(this, object.getSimperiumKey(), object);
    	}
    	
    	if (object.isModified())
    		channel.queueLocalChange(object);
    }

    /**
     * Tell the bucket to remove the object
     */
    public void remove(T object){
        // TODO: remove item from storage
        // TODO: tell listener that item is removed?
        storageProvider.removeObject(this, object.getSimperiumKey());
        channel.queueLocalDeletion(object);
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
                Simperium.log(String.format("Listener failed onObjectRemoved %s", listener));
            }
        }
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
        T object = get(key);
        if (object != null) {
            // this will call onObjectRemoved on the listener
            remove(object);
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
        return schema.getRemoteName(this);
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
        T object = schema.build(key, properties);
        // set the bucket that is managing this object
        object.setBucket(this);
        // set the diffable ghost object to determine local modifications
        object.setGhost(new Ghost(key, 0, properties));
        return object;
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
		Simperium.log(String.format("Build new object %s with ghost %s", object, object.getGhost()));
        return object;
    }
    /**
     * Save the ghost data and update the change version, tell the storage provider
     * that a new object has been added
     */
    protected void addObjectWithGhost(String changeVersion, Bucket.Ghost ghost){
        setChangeVersion(changeVersion);
        addObjectWithGhost(ghost);
    }
    /**
     * Add object from new ghost data, no corresponding change version so this
     * came from an index request
     */
    protected void addObjectWithGhost(Bucket.Ghost ghost){
        ghostStore.saveGhost(this, ghost);
        T object = buildObject(ghost);
        Simperium.log("Built object with ghost, add it");
        addObject(object);
    }
    /**
     * Update the ghost data
     */
    protected void updateObjectWithGhost(String changeVersion, Bucket.Ghost ghost){
        setChangeVersion(changeVersion);
        ghostStore.saveGhost(this, ghost);
        T object = buildObject(ghost);
        Simperium.log("Built object with ghost, updated it");
        updateObject(object);
    }
    protected void updateGhost(Bucket.Ghost ghost){
        ghostStore.saveGhost(this, ghost);
        Simperium.log("Update the ghost!");
    }
    protected Bucket.Ghost getGhost(String key){
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
        Simperium.log(String.format("Added an object, let's tell the storage provider %s %s", object, object.getGhost()));
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
                    Simperium.log(String.format("Listener failed onObjectAdded %s", listener), e);
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
                Simperium.log(String.format("Listener failed onObjectUpdated %s", listener));
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
        // TODO: tell the datastore to delete everything it has
        channel.reset();
		// Clear the ghost store
		ghostStore.reset();
    }
    /**
     * Get a single object object that matches key
     */
    public T get(String key){
        // Datastore constructs the object for us
        Map<String,java.lang.Object> properties = storageProvider.getObject(this, key);
		T object = schema.build(key, properties);
		Ghost ghost = ghostStore.getGhost(this, key);
		Simperium.log(String.format("Fetched ghost for %s %s", key, ghost));
		object.setBucket(this);
        object.setGhost(ghost);
        return object;
    }
    /**
     *
     */
    public void put(String key, T object){
        // storageProvider.putObject(this, key, object);
    }
    /**
     * Does bucket have at least the requested version?
     */
    public Boolean containsKey(String key){
        Ghost ghost = ghostStore.getGhost(this, key);
        return ghost != null;
    }
    /**
     * Ask storage if it has at least the requested version or newer
     */
    public Boolean hasKeyVersion(String key, Integer version){
        Ghost ghost = ghostStore.getGhost(this, key);
        return ghost != null && ghost.getVersion().equals(version);
    }
    /**
     * Which version of the key do we have
     */
    public Integer getKeyVersion(String key){
        Ghost ghost = ghostStore.getGhost(this, key);
        return ghost.getVersion();
    }

    public String uuid(){
        String key;
        do {
            key = Simperium.uuid();
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
            // log(String.format("Hello! %s", json.get(key).getClass().getName()));
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
