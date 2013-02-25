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
import java.util.HashMap;
import java.util.UUID;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class Bucket<T extends Bucket.Syncable> {
    /**
     * Represents a Simperium bucket which is a namespace where an app syncs a user's data
     * @param name the name to use for the bucket namespace
     * @param user provides a way to namespace data if a different user logs in
     */
    protected Bucket(String name, Schema<T>schema, User user, StorageProvider storageProvider){
        this.name = name;
        this.user = user;
        this.storageProvider = storageProvider;
        this.listeners = new Vector<Listener<T>>();
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
        void onObjectCreated(String key, T object);
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
    private static class Ghost implements Diffable {
        private String key;
        private Integer version;
        private Map<String, java.lang.Object> properties;
        private Ghost(String key, Integer version, Map<String, java.lang.Object> properties){
            this.key = key;
            this.version = version;
            this.properties = properties;
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
        public Diffable getGhost(){
            return ghost;
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
         * Send modifications over the socket to simperium
         */
        public void save(){
            getBucket().sync(this);
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
        public abstract T build(String key, Integer version, Map<String,java.lang.Object>properties);
    }
    /**
     * Basic implementation of Bucket.Schema for Bucket.Object
     */
    public static class ObjectSchema extends Schema<Object>{
        public String getRemoteName(Bucket bucket){
            return bucket.getName();
        }
        public Object build(String key, Integer version, Map<String,java.lang.Object> properties){
            return new Object(key, version, properties);
        }
    }
    /**
     * A generic object used to represent a single object from a bucket
     */
    public static class Object extends Syncable {
        
        private Bucket bucket;
        private String simperiumId;
        private Integer version = 0;
        private Diffable ghost;
        
        protected Map<String,java.lang.Object> properties;
        
        public Object(String key, Integer version, Map<String,java.lang.Object> properties){
            Simperium.log(String.format("Initializing with properties: %s", properties));
            this.simperiumId = key;
            this.version = version;
            this.properties = properties;
        }
     
        public Object(String key){
            this(key, new Integer(0), new HashMap<String, java.lang.Object>());
        }
    
        public Object(String key, Integer version, JSONObject objectData){
            this(key, version, Bucket.convertJSON(objectData));
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
            return version;
        }
        
        public Boolean isNew(){
            return version == null || version == 0;
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
            Simperium.log(String.format("Requesting diffable values %s", properties));
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
     * And now for the actual Bucket methods
     */
    // The name used for the Simperium namespace
    private String name;
    // User provides the access token for authentication
    private User user;
    // The channel that provides networking and change processing. This may be removed
    // and functionality will be provided via a listener interface of some kind
    // TODO: provide an interface for a class to observe when local changes are made
    // this is how the Channel associated with the bucket will determine if there are
    // local changes pending
    private Channel channel;
    // For storing the bucket listeners
    private Vector<Listener<T>> listeners;
    private StorageProvider storageProvider;
    private Schema<T> schema;
    
    /**
     * Tell the bucket to sync changes. 
     */
    public void sync(Syncable object){
        // TODO should we persists local modifications somewhere?
        // create the change id here so we can identify it in the future?
        // pass it off to the channel
    }
    
    /**
     * Add a listener to the bucket
     */
    public void addListener(Listener<T> listener){
        // TODO: Change listenrs to a set so a listener doesn't get added multiple times?
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
        Simperium.log(String.format("Saving change version %s", version));
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
        T object = schema.build(key, version, properties);
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
        object.setBucket(this);
        Vector<Listener> notify;
        synchronized(listeners){
            notify = new Vector<Listener>(listeners.size());
            notify.addAll(listeners);
        }
        Iterator<Listener> iterator = notify.iterator();
        while(iterator.hasNext()) {
            Listener listener = iterator.next();
            try {
                listener.onObjectCreated(object.getSimperiumId(), object);                
            } catch(Exception e) {
                Simperium.log(String.format("Listener failed onObjectCreated %s", listener));
            }
        }
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
        object.setBucket(this);
        storageProvider.addObject(this, object.getSimperiumId(), object);
        // notify listeners that an object has been added
        Vector<Listener<T>> notify;
        synchronized(listeners){
            notify = new Vector<Listener<T>>(listeners.size());
            notify.addAll(listeners);
        }
        
        Iterator<Listener<T>> iterator = notify.iterator();
        while(iterator.hasNext()) {
            Listener listener = iterator.next();
            try {
                listener.onObjectAdded(object.getSimperiumId(), object);
            } catch(Exception e) {
                Simperium.log(String.format("Listener failed onObjectAdded %s", listener), e);
            }
        }
    }
    /**
     * Updates an existing object
     */
    protected void updateObject(T object){
        object.setBucket(this);
        storageProvider.updateObject(this, object.getSimperiumId(), object);
        Vector<Listener> notify;
        synchronized(listeners){
            notify = new Vector<Listener>(listeners.size());
            notify.addAll(listeners);
        }
        
        Iterator<Listener> iterator = notify.iterator();
        while(iterator.hasNext()) {
            Listener listener = iterator.next();
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
            key = UUID.randomUUID().toString();
        } while(containsKey(key));
        return key;
    }
    
    public static Map<String,java.lang.Object> convertJSON(JSONObject json){
        Map<String,java.lang.Object> map = new HashMap<String,java.lang.Object>(json.length());
        Iterator keys = json.keys();
        while(keys.hasNext()){
            String key = (String)keys.next();
            try {
                java.lang.Object val = json.get(key);
                // log(String.format("Hello! %s", json.get(key).getClass().getName()));
                if (val.getClass().equals(JSONObject.class)) {
                    map.put(key, convertJSON((JSONObject) val));
                } else if (val.getClass().equals(JSONArray.class)) {
                    map.put(key, convertJSON((JSONArray) val));
                } else {
                    map.put(key, val);
                }
            } catch (JSONException e) {
                Simperium.log(String.format("Error: %s", e.getMessage()), e);
            }
        }
        return map;
        
    }
    
    public static List<java.lang.Object> convertJSON(JSONArray json){
        List<java.lang.Object> list = new ArrayList<java.lang.Object>(json.length());
        for (int i=0; i<json.length(); i++) {
            try {
                java.lang.Object val = json.get(i);
                if (val.getClass().equals(JSONObject.class)) {
                    list.add(convertJSON((JSONObject) val));
                } else if (val.getClass().equals(JSONArray.class)) {
                    list.add(convertJSON((JSONArray) val));
                } else {
                    list.add(val);
                }
            } catch (JSONException e) {
                Simperium.log(String.format("Error: %s", e.getMessage()), e);
            }

        }
        return list;
    }

}