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
    /**
     * Represents a Simperium bucket which is a namespace where an app syncs a user's data
     * @param name the name to use for the bucket namespace
     * @param user provides a way to namespace data if a different user logs in
     */
    protected Bucket(String name, Schema<T>schema, User user, StorageProvider storageProvider){
        this.name = name;
        this.user = user;
        this.storageProvider = storageProvider;
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
        void onObjectRemoved(String key, T object);
        void onObjectUpdated(String key, Integer version, T object);
        void onObjectAdded(String key, T object);
    }
    /**
     * The interface all objects must conform to in order to be able to be
     * tracked by Simperium. For a default implementation see BucketObject
     */
    public interface Diffable {
        // void setSimperiumId(String id);
        String getSimperiumId();
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
        private Ghost(String key, Integer version, Map<String, java.lang.Object> properties){
            this.key = key;
            this.version = version;
            // copy the properties
            this.properties = Bucket.deepCopy(properties);
        }
        public String getSimperiumId(){
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
        private Diffable getGhost(){
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
        private String simperiumId;
        private Diffable ghost;

        protected Map<String,java.lang.Object> properties;

        public Object(String key, Map<String,java.lang.Object> properties){
            this.simperiumId = key;
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

        public String getSimperiumId(){
            return simperiumId;
        }

        public Integer getVersion(){
            return ghost.getVersion();
        }

        public Boolean isNew(){
            return ghost.getVersion() == null || ghost.getVersion() == 0;
        }

        public String bucketName(){
            Bucket bucket = getBucket();
            if (bucket != null) {
                return bucket.getName();
            }
            return null;
        }

        public String toString(){
            return String.format("%s - %s", getBucket().getName(), getSimperiumId());
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
            return other.getBucket().equals(getBucket()) && other.getSimperiumId().equals(getSimperiumId());
        }
    }

    /**
     * Tell the bucket to sync changes.
     */
    public void sync(T object){
        // TODO should we persists local modifications somewhere?
        // TODO tell listener that items are updated?
        // create the change id here so we can identify it in the future?
        // pass it off to the channel
        storageProvider.updateObject(this, object.getSimperiumId(), object);
        channel.queueLocalChange(object);
    }

    /**
     * Tell the bucket to remove the object
     */
    public void remove(T object){
        // TODO: remove item from storage
        // TODO: tell listener that item is removed?
        storageProvider.removeObject(this, object.getSimperiumId());
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
                listener.onObjectRemoved(object.getSimperiumId(),object);
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
     * @param (Diffable) the object to sync
     */
    public void add(T object){
        if (!object.getBucket().equals(this)) {
            object.setBucket(this);
        }
        // TODO: sync the object over the socket
    }

    protected T buildObject(String key, Integer version, Map<String,java.lang.Object> properties){
        T object = schema.build(key, properties);
        // set the bucket that is managing this object
        object.setBucket(this);
        // set the diffable ghost object to determine local modifications
        object.setGhost(new Ghost(key, version, properties));
        // TODO: setup the ghost
        return object;
    }

    protected T buildObject(String key, Map<String,java.lang.Object> properties){
        return buildObject(key, 0, properties);
    }

    protected T buildObject(String key){
        return buildObject(key, 0, new HashMap<String,java.lang.Object>());
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
        T object = buildObject(uuid, 0, new HashMap<String,java.lang.Object>());
        addObject(object);
        return object;
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
        // Allows the storage provider to persist the object
        Boolean notifyListeners = true;
        if (!object.getBucket().equals(this)) {
            notifyListeners = true;
        }
        object.setBucket(this);
        storageProvider.addObject(this, object.getSimperiumId(), object);
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
                    listener.onObjectAdded(object.getSimperiumId(), object);
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
        storageProvider.updateObject(this, object.getSimperiumId(), object);
        Set<Listener<T>> notify;
        synchronized(listeners){
            notify = new HashSet<Listener<T>>(listeners.size());
            notify.addAll(listeners);
        }
        Iterator<Listener<T>> iterator = notify.iterator();
        while(iterator.hasNext()) {
            Listener<T> listener = iterator.next();
            try {
                listener.onObjectUpdated(object.getSimperiumId(), object.getVersion(), object);
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
    /**
     * Get a single object object that matches key
     */
    public T get(String key){
        // TODO: ask the datastore to find the object
        return storageProvider.getObject(this, key);
    }
    /**
     *
     */
    public void put(String key, T object){
        // storageProvider.putObject(this, key, object);
    }
    /**
     * Get a list of objects based on the provided keys
     */
    public List<T> getAll(String[] ... keys){
        return null;
    }
    /**
     * Ask the storage adapter for all of the entities in this bucket
     */
    public List<T> allEntities(){
        return (List<T>)storageProvider.allEntities(this);
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
