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

package com.simperium;

import com.simperium.User;
import java.util.Vector;

public class Bucket {
    /**
     * Bucket.Listener can be leveraged multiple ways if designed correctly
     *  - Channel's can listen for local changes to know when to send changes
     *    to simperium
     *  - Storage mechanisms can listen to all changes (local and remote) so they
     *    can perform their necessary save operations
     */
    public interface Listener {
        void onEntityUpdated();
        void onEntityRemoved();
        void onEntityAdded();
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
        
    /**
     * Represents a Simperium bucket which is a namespace where an app syncs a user's data
     * @param name the name to use for the bucket namespace
     * @param bucketType the class that is used to construct java objects from data
     * @param user provides a way to namespace data if a different user logs in
     */
    public Bucket(String name, Class<? extends Diffable>bucketType, User user){
        this.name = name;
        this.user = user;
        this.bucketType = bucketType;
    }
    /**
     * Get the bucket's namespace
     * @return (String) bucket's namespace
     */
    public String getName(){
        return name;
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

}