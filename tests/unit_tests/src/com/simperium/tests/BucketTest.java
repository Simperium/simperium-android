package com.simperium.tests;

import com.simperium.client.Change;
import com.simperium.client.RemoteChange;
import com.simperium.client.Channel;
import com.simperium.client.Channel.MessageEvent;
import com.simperium.client.Channel.OnMessageListener;
import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.GhostStoreProvider;
import com.simperium.client.User;
import com.simperium.storage.MemoryStore;

import com.simperium.util.Uuid;
import com.simperium.util.Logger;

import com.simperium.tests.models.Note;


import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.json.JSONArray;

public class BucketTest extends SimperiumTest {
    public static final String TAG="SimperiumTest";
    private Bucket<Note> mBucket;
    private BucketSchema<Note> mSchema;
    private User mUser;
    private GhostStoreProvider mGhostStore;
    private Channel<Note> mChannel;
    private OnMessageListener mOnMessageListener;

    private static String BUCKET_NAME="local-notes";

    protected void setUp() throws Exception {
        super.setUp();

        mUser = makeUser();

        mSchema = new Note.Schema();
        MemoryStore storage = new MemoryStore();
        mGhostStore = new MemoryGhostStore();
        mBucket = new Bucket<Note>(BUCKET_NAME, mSchema, mUser, storage.createStore(BUCKET_NAME, mSchema), mGhostStore);
        mOnMessageListener = new Channel.OnMessageListener(){
            @Override
            public void onMessage(MessageEvent event){
                Logger.log(TAG, String.format("Message: %s", event));
            }
        };
        mChannel = new Channel<Note>("fake-app-id", "awesome-client", mBucket, new FakeQueueSerializer(), mOnMessageListener);
        mBucket.setChannel(mChannel);
        mChannel.onConnect();
        mBucket.start();
    }

    public void testBucketName(){
        assertEquals(BUCKET_NAME, mBucket.getName());
        assertEquals(mSchema.getRemoteName(), mBucket.getRemoteName());
    }

    public void testBuildObject(){
        Note note = mBucket.newObject();
        assertTrue(note.isNew());
    }

    public void testSaveObject(){
        sendEmptyIndex();
        Note note = mBucket.newObject();
        note.setTitle("Hello World");
        assertTrue(note.isNew());
        acknowledge(note.save());
        assertFalse(note.isNew());
    }

    /**
     * Simulate an acknowledged change
     */
    protected void acknowledge(Change change){

        Integer sourceVersion = change.getVersion();
        Integer entityVersion = null;
        if (sourceVersion == null) {
            entityVersion = 1;
        } else {
            entityVersion = sourceVersion + 1;
        }
        List<String> ccids = new ArrayList<String>(1);
        String cv = Uuid.uuid().substring(0, 0xF);
        ccids.add(change.getChangeId());
        RemoteChange ack = new RemoteChange(mChannel.getSessionId(), change.getKey(), ccids, cv, sourceVersion, entityVersion, change.getDiff());
        tick();
        String message = String.format("%s:%s", Channel.COMMAND_CHANGE, remoteChangeToJson(ack));
        Logger.log(TAG, message);
        mChannel.receiveMessage(message);
        waitFor(change);
    }
    
    /**
     * Turn a remote change into JSON
     */
    protected JSONArray remoteChangeToJson(RemoteChange change){
        Map<String,Object> data = new HashMap<String,Object>();
        List<Object> changes = new ArrayList<Object>(1);
        changes.add(data);
        data.put(RemoteChange.CHANGE_IDS_KEY, change.getChangeIds());
        data.put(RemoteChange.CLIENT_KEY, change.getClientId());
        data.put(RemoteChange.ID_KEY, change.getKey());
        data.put(RemoteChange.CHANGE_VERSION_KEY, change.getChangeVersion());
        if (change.isError()) {
            data.put(RemoteChange.ERROR_KEY, change.getErrorCode());
            return Channel.serializeJSON(changes);
        }
        data.put(RemoteChange.ENTITY_VERSION_KEY, change.getObjectVersion());
        if (!change.isNew()) {
            data.put(RemoteChange.SOURCE_VERSION_KEY, change.getSourceVersion());
        }
        if (!change.isRemoveOperation()) {
            data.put(RemoteChange.OPERATION_KEY, RemoteChange.OPERATION_MODIFY);
            data.put(RemoteChange.VALUE_KEY, change.getPatch());            
        } else {
            data.put(RemoteChange.OPERATION_KEY, RemoteChange.OPERATION_REMOVE);
        }
        return Channel.serializeJSON(changes);
    }

    protected void sendEmptyIndex(){
        mChannel.receiveMessage("i:{index:[]}");
        tick();
    }
}
