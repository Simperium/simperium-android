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
 * Buckets should also provide a way for other objects to listen for when entities
 * get added, updated or removed due to operations coming in from the network.
 *
 * A bucket should also provide an interface that can listen to local changes so
 * that the channel can see when entities are changed on the client and push them
 * out to Simperium.
 *
 */

package com.simperium.client;

import com.simperium.SimperiumException;
import com.simperium.storage.StorageProvider.BucketStore;
import com.simperium.util.JSONDiff;
import com.simperium.util.Uuid;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;

public abstract class Bucket<T extends Syncable> {
    
    public interface Channel {
        public Change queueLocalChange(Syncable object);
        public Change queueLocalDeletion(Syncable object);
        public void log(int level, CharSequence message);
        public void start();
        public void stop();
        public void reset();
    }

    /**
     * Cursor for bucket data
     */
    public interface ObjectCursor<T extends Syncable> {

        /**
         * Return the current item's siperium key
         */
        public String getSimperiumKey();
        /**
         * Return the object for the current index in the cursor
         */
        public T getObject();

        /**
         * Go to the next item in the cursor
         */
        public boolean moveToNext();

    }

    public interface OnBeforeUpdateObjectListener<T extends Syncable> {
        void onBeforeUpdateObject(Bucket<T> bucket, T object);
    }

    public interface OnSaveObjectListener<T extends Syncable> {
        void onSaveObject(Bucket<T> bucket, T object);
    }

    public interface OnDeleteObjectListener<T extends Syncable> {
        void onDeleteObject(Bucket<T> bucket, T object);
    }

    public interface OnNetworkChangeListener<T extends Syncable> {
        void onChange(Bucket<T> bucket, ChangeType type, String key);
    }

    public interface Listener<T extends Syncable> extends
        OnSaveObjectListener<T>, OnDeleteObjectListener<T>,
        OnNetworkChangeListener<T>, OnBeforeUpdateObjectListener<T> {
            // implements all listener methods
    }

    public enum ChangeType {
        REMOVE, MODIFY, INDEX, RESET
    }

    public static final String TAG="Simperium.Bucket";
    // The name used for the Simperium namespace
    private String mName;
    // User provides the access token for authentication
    private User mUser;
    // The channel that provides networking and change processing.
    private Channel mChannel;
    // For storing the bucket listeners
    private Set<OnSaveObjectListener<T>> onSaveListeners =
        Collections.synchronizedSet(new HashSet<OnSaveObjectListener<T>>());
    private Set<OnDeleteObjectListener<T>> onDeleteListeners = 
        Collections.synchronizedSet(new HashSet<OnDeleteObjectListener<T>>());
    private Set<OnBeforeUpdateObjectListener<T>> onBeforeUpdateListeners =
        Collections.synchronizedSet(new HashSet<OnBeforeUpdateObjectListener<T>>());
    private Set<OnNetworkChangeListener<T>> onChangeListeners =
        Collections.synchronizedSet(new HashSet<OnNetworkChangeListener<T>>());

    private BucketStore<T> mStorage;
    private BucketSchema<T> mSchema;
    private GhostStorageProvider mGhostStore;
    final private Executor mExecutor;

    /**
     * Represents a Simperium bucket which is a namespace where an app syncs a user's data
     * @param name the name to use for the bucket namespace
     * @param user provides a way to namespace data if a different user logs in
     */
    public Bucket(Executor executor, String name, BucketSchema<T>schema, User user,
        BucketStore<T> storage, GhostStorageProvider ghostStore)
    throws BucketNameInvalid {
        mExecutor = executor;
        mName = name;
        mUser = user;
        mStorage = storage;
        mGhostStore = ghostStore;
        mSchema = schema;
        validateBucketName(name);
    }

    public void log(int level, CharSequence message) {
        if (mChannel == null) return;
        mChannel.log(level, message);
    }

    public void log(CharSequence message) {
        log(ChannelProvider.LOG_VERBOSE, message);
    }

    /**
     * Return the instance of this bucket's schema
     */
    public BucketSchema<T> getSchema() {
        return mSchema;
    }

    /**
     * Return the user for this bucket
     */
    public User getUser() {
        return mUser;
    }

    /**
     * Tell the bucket to sync changes.
     */
    public void sync(final T object) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Boolean modified = object.isModified();
                mStorage.save(object, mSchema.indexesFor(object));

                mChannel.queueLocalChange(object);

                if (modified) {
                    // Notify listeners that an object has been saved, this was
                    // triggered locally
                    notifyOnSaveListeners(object);
                }
            }
        });
    }

    /**
     * Delete the object from the bucket.
     * 
     * @param object the Syncable to remove from the bucket
     */
    public void remove(T object) {
        remove(object, true);
    }

    /**
     * Remove the object from the bucket. If isLocal is true, this will queue
     * an operation to sync with the Simperium service.
     * 
     * @param object The Syncable to remove from the bucket
     * @param isLocal if the operation originates from this client
     */
    private void remove(final T object, final boolean isLocal) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isLocal)
                    mChannel.queueLocalDeletion(object);

                mStorage.delete(object);
                notifyOnDeleteListeners(object);
            }
        });
    }

    /**
     * Given the key for an object in the bucket, remove it if it exists
     */
    private void removeObjectWithKey(String key)
    throws BucketObjectMissingException {
        T object = get(key);
        if (object != null) {
            // this will call onObjectRemoved on the listener
            remove(object, false);
        }
    }

    /**
     * Get the bucket's namespace
     * @return (String) bucket's namespace
     */
    public String getName() {
        return mName;
    }

    public String getRemoteName() {
        return mSchema.getRemoteName();
    }

    public Boolean hasChangeVersion() {
        return mGhostStore.hasChangeVersion(this);
    }

    public Boolean hasChangeVersion(String version) {
        return mGhostStore.hasChangeVersion(this, version);
    }

    public String getChangeVersion() {
        String version = mGhostStore.getChangeVersion(this);
        if (version == null) {
            version = "";
        }
        return version;
    }

    public void indexComplete(String changeVersion) {
        setChangeVersion(changeVersion);
        notifyOnNetworkChangeListeners(ChangeType.INDEX);
    }

    public void setChangeVersion(String version) {
        mGhostStore.setChangeVersion(this, version);
    }

    // starts tracking the object
    /**
     * Add an object to the bucket so simperium can start syncing it. Must
     * conform to the Diffable interface. So simperium can diff/apply patches.
     *
     */
    public void add(T object) {
        if (!object.getBucket().equals(this)) {
            object.setBucket(this);
        }
    }

    protected T buildObject(String key, JSONObject properties) {
        return buildObject(new Ghost(key, 0, properties));
    }


    protected T buildObject(String key) {
        return buildObject(key, new JSONObject());
    }
    
    protected T buildObject(Ghost ghost) {
        T object = mSchema.buildWithDefaults(ghost.getSimperiumKey(), JSONDiff.deepCopy(ghost.getDiffableValue()));
        object.setGhost(ghost);
        object.setBucket(this);
        return object;
    }

    /**
     * Get a single object object that matches key
     */
    public T get(String key) throws BucketObjectMissingException {
        // Datastore constructs the object for us
        Ghost ghost = null;
        try {
            ghost = mGhostStore.getGhost(this, key);
        } catch (GhostMissingException e) {
            throw(new BucketObjectMissingException(String.format("Bucket %s does not have object %s", getName(), key)));
        }
        T object = mStorage.get(key);
        if (object == null) {
            throw(new BucketObjectMissingException(String.format("Storage provider for bucket:%s did not have object %s", getName(), key)));
        }
        // Logger.log(TAG, String.format("Fetched ghost for %s %s", key, ghost));
        object.setBucket(this);
        object.setGhost(ghost);
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
     * Returns a new object tracked by this bucket
     */
    public T newObject() {
        try {
            return newObject(uuid());
        } catch (BucketObjectNameInvalid e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a new object with the given uuid
     * return null if the uuid exists?
     */
    public T newObject(String key)
    throws BucketObjectNameInvalid {
        return insertObject(key, new JSONObject());
    }

    /**
     * 
     */
    public T insertObject(String key, JSONObject properties)
    throws BucketObjectNameInvalid {
        if (key == null)
            throw new BucketObjectNameInvalid(key);

        String name = key.trim();
        validateObjectName(name);
        T object = buildObject(name, properties);
        object.setBucket(this);
        Ghost ghost = new Ghost(name, 0, new JSONObject());
        object.setGhost(ghost);
        mGhostStore.saveGhost(this, ghost);
        return object;
    }

    public int count() {
        return count(query());
    }

    public int count(Query<T> query) {
        return mStorage.count(query);
    }

    /**
     * Find all objects
     */
    abstract public ObjectCursor<T> allObjects();

    /**
     * Search using a query
     */
    abstract public ObjectCursor<T> searchObjects(Query<T> query);

    /**
     * Build a query for this object
     */
    public Query<T> query() {
        return new Query<T>(this);
    }


    /**
     * Add object from new ghost data, no corresponding change version so this
     * came from an index request
     */
    protected void addObjectWithGhost(final Ghost ghost) {
        addObjectWithGhost(ghost, null);
    }

    /**
     * 
     */
    protected void addObjectWithGhost(final Ghost ghost, final Runnable runnable) {
        mExecutor.execute(new Runnable() {

            @Override
            public void run() {
                mGhostStore.saveGhost(Bucket.this, ghost);
                T object = buildObject(ghost);
                addObject(object);

                if (runnable != null) {
                    runnable.run();
                }

            }

        });
    }


    /**
     * Update the ghost data
     */
    protected void updateObjectWithGhost(final Ghost ghost) {
        mGhostStore.saveGhost(Bucket.this, ghost);
        T object = mSchema.build(ghost.getSimperiumKey(), ghost.getDiffableValue());
        updateObject(object);
    }

    protected void updateGhost(final Ghost ghost, final Runnable complete) {
        mExecutor.execute(new Runnable() {

            @Override
            public void run() {
                // find the object
                try {
                    T object = get(ghost.getSimperiumKey());
                    if (object.isModified()) {
                        // Attempt to merge local changes with the new ghost
                        Ghost localGhost = object.getGhost();
                        JSONObject localGhostProperties = localGhost.getDiffableValue();

                        // Get diff of local ghost vs. local object
                        JSONObject localModifications;
                        try {
                            localModifications = JSONDiff.diff(localGhostProperties, object.getDiffableValue());
                        } catch (JSONException e) {
                            localModifications = new JSONObject();
                        }

                        // Get diff of remote ghost vs. local ghost
                        JSONObject remoteModifications;
                        try {
                            remoteModifications = JSONDiff.diff(localGhostProperties, ghost.getDiffableValue());
                        } catch (JSONException e) {
                            remoteModifications = new JSONObject();
                        }

                        try {
                            JSONObject localPatch = localModifications.getJSONObject(JSONDiff.DIFF_VALUE_KEY);
                            JSONObject remotePatch = remoteModifications.getJSONObject(JSONDiff.DIFF_VALUE_KEY);

                            JSONObject transformedDiff = JSONDiff.transform(localPatch, remotePatch, localGhostProperties);
                            JSONObject updatedProperties = JSONDiff.deepCopy(ghost.getDiffableValue());
                            updatedProperties = JSONDiff.apply(updatedProperties, transformedDiff);

                            mSchema.update(object, updatedProperties);
                            object.setGhost(ghost);
                            updateObject(object);
                        } catch (JSONException e) {
                            // Could not apply patch, update from the ghost
                            updateObjectWithGhost(ghost);
                        } catch (IllegalArgumentException e) {
                            // JSONDiff argument failure, update from the ghost
                            updateObjectWithGhost(ghost);
                        }
                    } else {
                        // Apply the new ghost to the unmodified local object
                        updateObjectWithGhost(ghost);
                    }

                    // Notify listeners that the object has changed
                    notifyOnNetworkChangeListeners(ChangeType.MODIFY, object.getSimperiumKey());
                } catch (BucketObjectMissingException e) {
                    // The object doesn't exist, insert the new object
                    updateObjectWithGhost(ghost);
                }

                if (complete != null) {
                    complete.run();
                }
            }

        });
    }

    public Ghost getGhost(String key) throws GhostMissingException {
        return mGhostStore.getGhost(this, key);
    }
    /**
     * Add a new object with corresponding change version
     */
    protected void addObject(String changeVersion, T object) {
        addObject(object);
        setChangeVersion(changeVersion);
    }
    /**
     * Adds a new object to the bucket
     */
    protected void addObject(T object) {
        if (object.getGhost() == null) {
            object.setGhost(new Ghost(object.getSimperiumKey()));
        }
        
        // Allows the storage provider to persist the object
        Boolean notifyListeners = true;
        if (!object.getBucket().equals(this)) {
            notifyListeners = true;
        }
        object.setBucket(this);
        mStorage.save(object, mSchema.indexesFor(object));
        // notify listeners that an object has been added
    }

    /**
     * Updates an existing object
     */
    protected void updateObject(T object) {
        object.setBucket(this);
        mStorage.save(object, mSchema.indexesFor(object));
    }

    /**
     *
     */
    protected void updateObject(String changeVersion, T object) {
        updateObject(object);
        setChangeVersion(changeVersion);
    }

    public void addListener(Listener<T> listener) {
        addOnSaveObjectListener(listener);
        addOnBeforeUpdateObjectListener(listener);
        addOnDeleteObjectListener(listener);
        addOnNetworkChangeListener(listener);
    }

    public void removeListener(Listener<T> listener) {
        removeOnSaveObjectListener(listener);
        removeOnBeforeUpdateObjectListener(listener);
        removeOnDeleteObjectListener(listener);
        removeOnNetworkChangeListener(listener);
    }

    public void addOnSaveObjectListener(OnSaveObjectListener<T> listener) {
        onSaveListeners.add(listener);
    }

    public void removeOnSaveObjectListener(OnSaveObjectListener<T> listener) {
        onSaveListeners.remove(listener);
    }

    public void addOnDeleteObjectListener(OnDeleteObjectListener<T> listener) {
        onDeleteListeners.add(listener);
    }

    public void removeOnDeleteObjectListener(OnDeleteObjectListener<T> listener) {
        onDeleteListeners.remove(listener);
    }

    public void addOnNetworkChangeListener(OnNetworkChangeListener<T> listener) {
        onChangeListeners.add(listener);
    }

    public void removeOnNetworkChangeListener(OnNetworkChangeListener<T> listener) {
        onChangeListeners.remove(listener);
    }

    public void addOnBeforeUpdateObjectListener(OnBeforeUpdateObjectListener<T> listener) {
        onBeforeUpdateListeners.add(listener);
    }

    public void removeOnBeforeUpdateObjectListener(OnBeforeUpdateObjectListener<T> listener) {
        onBeforeUpdateListeners.remove(listener);
    }

    public void notifyOnSaveListeners(T object) {
        Set<OnSaveObjectListener<T>> notify = new HashSet<OnSaveObjectListener<T>>(onSaveListeners);

        Iterator<OnSaveObjectListener<T>> iterator = notify.iterator();
        while(iterator.hasNext()) {
            OnSaveObjectListener<T> listener = iterator.next();
            try {
                listener.onSaveObject(this, object);
            } catch(Exception e) {
                // Logger.log(TAG, String.format("Listener failed onSaveObject %s", listener), e);
            }
        }
    }

    public void notifyOnDeleteListeners(T object) {
        Set<OnDeleteObjectListener<T>> notify = new HashSet<OnDeleteObjectListener<T>>(onDeleteListeners);
        
        Iterator<OnDeleteObjectListener<T>> iterator = notify.iterator();
        while(iterator.hasNext()) {
            OnDeleteObjectListener<T> listener = iterator.next();
            try {
                listener.onDeleteObject(this, object);
            } catch(Exception e) {
                // Logger.log(TAG, String.format("Listener failed onDeleteObject %s", listener), e);
            }
        }
    }

    public void notifyOnBeforeUpdateObjectListeners(T object) {
        Set<OnBeforeUpdateObjectListener<T>> notify = new HashSet<OnBeforeUpdateObjectListener<T>>(onBeforeUpdateListeners);

        Iterator<OnBeforeUpdateObjectListener<T>> iterator = notify.iterator();
        while(iterator.hasNext()) {
            OnBeforeUpdateObjectListener<T> listener = iterator.next();
            try {
                listener.onBeforeUpdateObject(this, object);
            } catch(Exception e) {
                // Logger.log(TAG, String.format("Listener failed onBeforeUpdateObject %s", listener), e);
            }
        }
    }

    public void notifyOnNetworkChangeListeners(ChangeType type) {
        notifyOnNetworkChangeListeners(type, null);
    }

    public void notifyOnNetworkChangeListeners(ChangeType type, String key) {
        Set<OnNetworkChangeListener> notify =
            new HashSet<OnNetworkChangeListener>(onChangeListeners);

        Iterator<OnNetworkChangeListener> iterator = notify.iterator();
        while(iterator.hasNext()) {
            OnNetworkChangeListener listener = iterator.next();
            try {
                listener.onChange(this, type, key);
            } catch(Exception e) {
                // Logger.log(TAG, String.format("Listener failed onChange %s", listener), e);
            }
        }
    }


    public void setChannel(Channel channel) {
        mChannel = channel;
    }

    /**
     * Initialize the bucket to start tracking changes.
     */
    public void start() {
        mChannel.start();
    }

    public void stop() {
        mChannel.stop();
    }


    public void reset() {
        mStorage.reset();
        // Clear the ghost store
        mGhostStore.resetBucket(this);
        mChannel.reset();
        stop();

        notifyOnNetworkChangeListeners(ChangeType.RESET);
    }
    /**
     * Does bucket have at least the requested version?
     */
    public Boolean containsKey(String key) {
        return mGhostStore.hasGhost(this, key);
    }
    /**
     * Ask storage if it has at least the requested version or newer
     */
    public Boolean hasKeyVersion(String key, Integer version) {
        try {
            Ghost ghost = mGhostStore.getGhost(this, key);
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
        Ghost ghost = mGhostStore.getGhost(this, key);
        return ghost.getVersion();
    }

    /**
     * Submit a Runnable to this Bucket's executor
     */
    public void executeAsync(Runnable task) {
        mExecutor.execute(task);
    }

    public String uuid() {
        String key;
        do {
            key = Uuid.uuid();
        } while(containsKey(key));
        return key;
    }

    public Ghost acknowledgeChange(RemoteChange remoteChange, Change change)
    throws RemoteChangeInvalidException {
        Ghost ghost = null;
        if (!remoteChange.isRemoveOperation()) {
            try {
                T object = get(remoteChange.getKey());
                // apply the diff to the underyling object
                ghost = remoteChange.apply(object.getGhost());
                mGhostStore.saveGhost(this, ghost);
                // update the object's ghost
                object.setGhost(ghost);
            } catch (BucketObjectMissingException e) {
                throw(new RemoteChangeInvalidException(remoteChange, e));
            }
        } else {
            mGhostStore.deleteGhost(this, remoteChange.getKey());
        }
        setChangeVersion(remoteChange.getChangeVersion());
        remoteChange.setApplied();
        // TODO: remove changes don't need ghosts, need to rethink this a bit
        return ghost;
    }

    public Ghost applyRemoteChange(RemoteChange change)
    throws RemoteChangeInvalidException {
        Ghost updatedGhost = null;
        if (change.isRemoveOperation()) {
            try {
                removeObjectWithKey(change.getKey());
                mGhostStore.deleteGhost(this, change.getKey());
            } catch (BucketObjectMissingException e) {
                throw(new RemoteChangeInvalidException(change, e));
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

                    notifyOnBeforeUpdateObjectListeners(object);
                }

                Ghost ghost = object.getGhost();
                JSONObject localModifications = null;
                JSONObject currentProperties = ghost.getDiffableValue();

                try {
                    localModifications = JSONDiff.diff(currentProperties, object.getDiffableValue());
                } catch (JSONException e) {
                    localModifications = new JSONObject();
                }

                // updates the ghost and sets it on the object
                updatedGhost = change.apply(ghost);
                JSONObject updatedProperties = JSONDiff.deepCopy(updatedGhost.getDiffableValue());

                // persist the ghost to storage
                mGhostStore.saveGhost(this, updatedGhost);
                object.setGhost(updatedGhost);

                // allow the schema to update the object instance with the new
                if (isNew) {
                    mSchema.updateWithDefaults(object, updatedProperties);
                    addObject(object);
                } else {

                    if (localModifications != null && localModifications.length() > 0) {
                        try {
                            JSONObject incomingDiff = change.getPatch();
                            JSONObject localDiff = localModifications.getJSONObject(JSONDiff.DIFF_VALUE_KEY);

                            JSONObject transformedDiff = JSONDiff.transform(localDiff, incomingDiff, currentProperties);

                            updatedProperties = JSONDiff.apply(updatedProperties, transformedDiff);

                        } catch (JSONException e) {
                            // could not transform properties
                            // continue with updated properties
                        } catch (IllegalArgumentException e) {
                            // could not apply diff, continue
                        }
                    }

                    mSchema.update(object, updatedProperties);
                    updateObject(object);
                }

            } catch(SimperiumException e) {
                // Logger.log(TAG, String.format("Unable to apply remote change %s", change), e);
                throw(new RemoteChangeInvalidException(change, e));
            }
        }
        setChangeVersion(change.getChangeVersion());
        change.setApplied();
        ChangeType type = change.isRemoveOperation() ? ChangeType.REMOVE : ChangeType.MODIFY;
        notifyOnNetworkChangeListeners(type, change.getKey());
        return updatedGhost;
    }

    static public final String BUCKET_OBJECT_NAME_REGEX = "^[a-zA-Z0-9_\\.\\-%@]{1,256}$";

    public static void validateObjectName(String name)
    throws BucketObjectNameInvalid {
        if (name == null || !name.matches(BUCKET_OBJECT_NAME_REGEX)) {
            throw new BucketObjectNameInvalid(name);
        }
    }

    static public final String BUCKET_NAME_REGEX = "^[a-zA-Z0-9_\\.\\-%]{1,64}$";

    public static void validateBucketName(String name)
    throws BucketNameInvalid {
        if (name == null || !name.matches(BUCKET_NAME_REGEX)) {
            throw new BucketNameInvalid(name);
        }
    }

}
