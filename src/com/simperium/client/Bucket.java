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

import com.simperium.storage.StorageProvider.BucketStore;
import com.simperium.util.Logger;
import com.simperium.util.Uuid;
import com.simperium.util.JSONDiff;

import android.database.Cursor;
import android.database.CursorWrapper;
import android.os.CancellationSignal;

public class Bucket<T extends Syncable> {
    
    public interface ChannelProvider<T extends Syncable> {
        public Change<T> queueLocalChange(T object);
        public Change<T> queueLocalDeletion(T object);
        public boolean isIdle();
        public void start();
        public void reset();
    }

    public interface OnSaveObjectListener<T extends Syncable> {
        void onSaveObject(T object);
    }

    public interface OnDeleteObjectListener<T extends Syncable> {
        void onDeleteObject(T object);
    }

    public interface OnNetworkChangeListener {
        void onChange(ChangeType type, String key);
    }

    public enum ChangeType {
        REMOVE, MODIFY, INDEX
    }

    public static final String TAG="Simperium.Bucket";
    // The name used for the Simperium namespace
    private String name;
    // User provides the access token for authentication
    private User user;
    // The channel that provides networking and change processing.
    private ChannelProvider<T> channel;
    // For storing the bucket listeners
    private Set<OnSaveObjectListener<T>> onSaveListeners =
        Collections.synchronizedSet(new HashSet<OnSaveObjectListener<T>>());
    private Set<OnDeleteObjectListener<T>> onDeleteListeners = 
        Collections.synchronizedSet(new HashSet<OnDeleteObjectListener<T>>());
    private Set<OnNetworkChangeListener> onChangeListeners =
        Collections.synchronizedSet(new HashSet<OnNetworkChangeListener>());

    private BucketStore<T> storage;
    private BucketSchema<T> schema;
    private GhostStoreProvider ghostStore;
    private ObjectCache<T> cache;
    /**
     * Represents a Simperium bucket which is a namespace where an app syncs a user's data
     * @param name the name to use for the bucket namespace
     * @param user provides a way to namespace data if a different user logs in
     */
    public Bucket(String name, BucketSchema<T>schema, User user, BucketStore<T> storage, GhostStoreProvider ghostStore){
        this(name, schema, user, storage, ghostStore, null);
        cache = ObjectCache.buildCache(this);
    }

    public Bucket(String name, BucketSchema<T>schema, User user, BucketStore<T> storage, GhostStoreProvider ghostStore, ObjectCache cache){
        this.name = name;
        this.user = user;
        this.storage = storage;
        this.ghostStore = ghostStore;
        this.schema = schema;
        this.cache = cache;
    }
    /**
     * Return the user for this bucket
     */
    public User getUser(){
        return user;
    }
    /**
     * If the channel is running or expecting to process changes
     */
    public boolean isIdle(){
        return channel.isIdle();
    }

    /**
     * Cursor for bucket data
     */
    public interface ObjectCursor<T extends Syncable> extends Cursor {

        /**
         * Return the current item's siperium key
         */
        public String getSimperiumKey();
        /**
         * Return the object for the current index in the cursor
         */
        public T getObject();

    }

    private class BucketCursor extends CursorWrapper implements ObjectCursor<T> {

        private ObjectCursor<T> cursor;

        BucketCursor(ObjectCursor<T> cursor){
            super(cursor);
            this.cursor = cursor;
        }

        @Override
        public String getSimperiumKey(){
            return cursor.getSimperiumKey();
        }

        @Override
        public T getObject(){
            String key = getSimperiumKey();
            T object = cache.get(key);
            if (object != null) {
                return object;
            }
            object = cursor.getObject();
            try {
                Ghost ghost = ghostStore.getGhost(Bucket.this, key);
                object.setGhost(ghost);
            } catch (GhostMissingException e) {
                object.setGhost(new Ghost(key, 0, new HashMap<String,Object>()));
            }
            object.setBucket(Bucket.this);
            cache.put(key, object);
            return object;
        }

    }
    /**
     * Tell the bucket to sync changes.
     */
    public Change<T> sync(T object){
        Logger.log(TAG, String.format("Syncing object %s", object));
        storage.save(object, schema.indexesFor(object));
        
        if (object.isModified()){
            Change<T> change = channel.queueLocalChange(object);
            // Notify listeners that an object has been saved, this was
            // triggered locally
            notifyOnSaveListeners(object);
            return change;
        }
        return null;
    }

    /**
     * Tell the bucket to remove the object
     */
    public Change<T> remove(T object){
        cache.remove(object.getSimperiumKey());
        storage.delete(object);
        Change<T> change = channel.queueLocalDeletion(object);
        notifyOnDeleteListeners(object);
        return change;
    }

    /**
     * Update the change version and remove the object with the given key
     */
    protected void removeObjectWithKey(String changeVersion, String key)
    throws BucketObjectMissingException {
        removeObjectWithKey(key);
        setChangeVersion(changeVersion);
    }
    /**
     * Given the key for an object in the bucket, remove it if it exists
     */
    protected void removeObjectWithKey(String key)
    throws BucketObjectMissingException {
        T object = get(key);
        if (object != null) {
            // this will call onObjectRemoved on the listener
            remove(object);
        }
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
        String version = ghostStore.getChangeVersion(this);
        if (version == null) {
            version = "";
        }
        return version;
    }

    public void indexComplete(String changeVersion){
        setChangeVersion(changeVersion);
        notifyOnNetworkChangeListeners(ChangeType.INDEX, null);
    }

    public void setChangeVersion(String version){
        Logger.log(TAG, String.format("Updating cv to %s", version));
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
        T object = schema.build(ghost.getSimperiumKey(), JSONDiff.deepCopy(ghost.getDiffableValue()));
        object.setGhost(ghost);
        object.setBucket(this);
        return object;
    }

    /**
     * Find all objects
     */
    public ObjectCursor<T> allObjects(){
        return allObjects(null);
    }

    /**
     * Find all objects and provide a way to cancel query
     */
    public ObjectCursor<T> allObjects(CancellationSignal cancel){
        return new BucketCursor(storage.all(cancel));
    }

    /**
     * Search using a query
     */
    public ObjectCursor<T> searchObjects(Query<T> query){
        return searchObjects(query, null);
    }

    /**
     * Search using a query and provide a way to cancel query
     */
    public ObjectCursor<T> searchObjects(Query<T> query, CancellationSignal cancel){
        return new BucketCursor(storage.search(query, cancel));
    }

    /**
     * Support cancelation
     */
    /**
     * Build a query for this object
     */
    public Query<T> query(){
        return new Query<T>(this);
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
        object = storage.get(key);
        if (object == null) {
            throw(new BucketObjectMissingException(String.format("Storage provider for bucket:%s did not have object %s", getName(), key)));
        }
        Logger.log(TAG, String.format("Fetched ghost for %s %s", key, ghost));
        object.setBucket(this);
        object.setGhost(ghost);
        cache.put(key, object);
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
    public T newObject(String key){
        return insertObject(key, new HashMap<String,Object>());
    }

    /**
     * 
     */
    public T insertObject(String key, Map<String,Object> properties){
        T object = buildObject(key, properties);
        object.setBucket(this);
        Ghost ghost = new Ghost(key, 0, new HashMap<String,Object>());
        object.setGhost(ghost);
        ghostStore.saveGhost(this, ghost);
        cache.put(key, object);
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
        Logger.log(TAG, "Built object with ghost, add it");
        addObject(object);
    }
    /**
     * Update the ghost data
     */
    protected void updateObjectWithGhost(String changeVersion, Ghost ghost){
        setChangeVersion(changeVersion);
        ghostStore.saveGhost(this, ghost);
        T object = buildObject(ghost);
        Logger.log(TAG, "Built object with ghost, updated it");
        updateObject(object);
    }
    protected void updateGhost(Ghost ghost){
        ghostStore.saveGhost(this, ghost);
        Logger.log(TAG, "Update the ghost!");
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
        if (object.getGhost() == null) {
            object.setGhost(new Ghost(object.getSimperiumKey(), 0, new HashMap<String, java.lang.Object>()));
        }
        
        // Allows the storage provider to persist the object
        Boolean notifyListeners = true;
        if (!object.getBucket().equals(this)) {
            notifyListeners = true;
        }
        object.setBucket(this);
        storage.save(object, schema.indexesFor(object));
        // notify listeners that an object has been added
    }
    /**
     * Updates an existing object
     */
    protected void updateObject(T object){
        object.setBucket(this);
        storage.save(object, schema.indexesFor(object));
    }
    /**
     *
     */
    protected void updateObject(String changeVersion, T object){
        updateObject(object);
        setChangeVersion(changeVersion);
    }

    public void registerOnSaveObjectListener(OnSaveObjectListener<T> listener){
        Logger.log(TAG, String.format("Registering OnSaveObjectListener %s", listener));
        onSaveListeners.add(listener);
    }

    public void unregisterOnSaveObjectListener(OnSaveObjectListener<T> listener){
        onSaveListeners.remove(listener);
    }

    public void registerOnDeleteObjectListener(OnDeleteObjectListener<T> listener){
        onDeleteListeners.add(listener);
    }

    public void unregisterOnDeleteObjectListener(OnDeleteObjectListener<T> listener){
        onDeleteListeners.remove(listener);
    }

    public void registerOnNetworkChangeListener(OnNetworkChangeListener listener){
        onChangeListeners.add(listener);
    }

    public void unregisterOnNetworkChangeListener(OnNetworkChangeListener listener){
        onChangeListeners.remove(listener);
    }

    protected void notifyOnSaveListeners(T object){
        Set<OnSaveObjectListener<T>> notify = new HashSet<OnSaveObjectListener<T>>(onSaveListeners);
        Logger.log(TAG, String.format("Notifying OnSaveObjectListener %d", notify.size()));

        Iterator<OnSaveObjectListener<T>> iterator = notify.iterator();
        while(iterator.hasNext()) {
            OnSaveObjectListener<T> listener = iterator.next();
            try {
                listener.onSaveObject(object);
            } catch(Exception e) {
                Logger.log(TAG, String.format("Listener failed onSaveObject %s", listener), e);
            }
        }
    }

    protected void notifyOnDeleteListeners(T object){
        Set<OnDeleteObjectListener<T>> notify = new HashSet<OnDeleteObjectListener<T>>(onDeleteListeners);

        Iterator<OnDeleteObjectListener<T>> iterator = notify.iterator();
        while(iterator.hasNext()) {
            OnDeleteObjectListener<T> listener = iterator.next();
            try {
                listener.onDeleteObject(object);
            } catch(Exception e) {
                Logger.log(TAG, String.format("Listener failed onDeleteObject %s", listener), e);
            }
        }
    }

    protected void notifyOnNetworkChangeListeners(ChangeType type, String key){
        Set<OnNetworkChangeListener> notify =
            new HashSet<OnNetworkChangeListener>(onChangeListeners);

        Iterator<OnNetworkChangeListener> iterator = notify.iterator();
        while(iterator.hasNext()) {
            OnNetworkChangeListener listener = iterator.next();
            try {
                listener.onChange(type, key);
            } catch(Exception e) {
                Logger.log(TAG, String.format("Listener failed onChange %s", listener), e);
            }
        }
    }


    public void setChannel(ChannelProvider channel){
        this.channel = channel;
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
        storage.reset();
        ghostStore.resetBucket(this);
        channel.reset();
        // Clear the ghost store
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

    public Ghost acknowledgeChange(RemoteChange remoteChange, Change<T> change)
    throws RemoteChangeInvalidException {
        Logger.log(TAG, String.format("Acknowleding change %s", change));
        Ghost ghost = null;
        if (!remoteChange.isRemoveOperation()) {
            try {
                T object = get(remoteChange.getKey());
                // apply the diff to the underyling object
                ghost = remoteChange.apply(object);
                ghostStore.saveGhost(this, ghost);
                // update the object's ghost
                object.setGhost(ghost);
            } catch (BucketObjectMissingException e) {
                throw(new RemoteChangeInvalidException(e));
            }
        } else {
            ghostStore.deleteGhost(this, remoteChange.getKey());
        }
        setChangeVersion(remoteChange.getChangeVersion());
        remoteChange.setApplied();
        Logger.log(TAG, String.format("Marking change applied %s %b", change, change.isComplete()));
        // TODO: remove changes don't need ghosts, need to rethink this a bit
        return ghost;
    }

    public Ghost applyRemoteChange(RemoteChange change)
    throws RemoteChangeInvalidException {
        Ghost ghost = null;
        if (change.isRemoveOperation()) {
            try {
                removeObjectWithKey(change.getKey());
                ghostStore.deleteGhost(this, change.getKey());
            } catch (BucketObjectMissingException e) {
                throw(new RemoteChangeInvalidException(e));
            }
        } else {
            try {
                T object = null;
                Boolean isNew = false;
                if (change.isAddOperation()) {
                    object = newObject(change.getKey());
                    isNew = true;
                } else {
                    object = getObject(change.getKey());
                    isNew = false;
                }
                // updates the ghost and sets it on the object
                ghost = change.apply(object);
                // persist the ghost to storage
                ghostStore.saveGhost(this, ghost);
                // allow the schema to update the object instance with the new
                // data
                schema.update(object, JSONDiff.deepCopy(ghost.getDiffableValue()));
                if (isNew) {
                    addObject(object);
                } else {
                    updateObject(object);
                }
            } catch(BucketObjectMissingException e) {
                Logger.log(TAG, "Unable to apply remote change", e);
                throw(new RemoteChangeInvalidException(e));
            }
        }
        setChangeVersion(change.getChangeVersion());
        change.setApplied();
        ChangeType type = change.isRemoveOperation() ? ChangeType.REMOVE : ChangeType.MODIFY;
        notifyOnNetworkChangeListeners(type, change.getKey());
        return ghost;
    }



}
