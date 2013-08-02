package com.simperium.testapp;

import static com.simperium.testapp.TestHelpers.*;

import com.simperium.client.Change;
import com.simperium.client.RemoteChange;
import com.simperium.client.Bucket;
import com.simperium.client.ChannelProvider;
import com.simperium.client.BucketSchema;
import com.simperium.client.ObjectCache;
import com.simperium.client.GhostStoreProvider;
import com.simperium.client.User;
import com.simperium.client.Ghost;
import com.simperium.storage.MemoryStore;
import com.simperium.storage.StorageProvider.BucketStore;

import com.simperium.util.Uuid;
import com.simperium.util.Logger;

import com.simperium.testapp.models.Note;
import com.simperium.testapp.mock.MockChannel;
import com.simperium.testapp.mock.MockCache;
import com.simperium.testapp.mock.MockGhostStore;

import org.json.JSONArray;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class BucketTest extends SimperiumTest {

    public static final String TAG="SimperiumTest";
    private Bucket<Note> mBucket;
    private BucketSchema<Note> mSchema;
    private User mUser;
    private GhostStoreProvider mGhostStore;
    private MockChannel mChannel;
    private BucketStore<Note> mBucketStore;

    private static String BUCKET_NAME="local-notes";

    protected void setUp() throws Exception {
        super.setUp();

        mUser = makeUser();

        mSchema = new Note.Schema();
        MemoryStore storage = new MemoryStore();
        mGhostStore = new MockGhostStore();
        ObjectCache<Note> cache = new ObjectCache<Note>(new MockCache<Note>());
        mBucketStore = storage.createStore(BUCKET_NAME, mSchema);
        mBucket = new Bucket<Note>(BUCKET_NAME, mSchema, mUser, mBucketStore, mGhostStore, cache);
        mChannel = new MockChannel(mBucket);
        mBucket.setChannel(mChannel);
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
        Note note = mBucket.newObject();
        note.setTitle("Hello World");
        assertTrue(note.isNew());
        note.save();
        // acknowledge(note.save());
        assertFalse(note.isNew());
    }

    public void testObjectHistory() throws Exception {
        String key = "fake-history";
        // put a fake ghost in the ghost store
        Map<String,Object> properties = new HashMap<String,Object>(2);
        properties.put("title", "Hello World");
        properties.put("content", "Lorem ipsum");

        mGhostStore.saveGhost(mBucket, new Ghost(key, 3, properties));

        RevisionsReceiver history = new RevisionsReceiver();
        ChannelProvider.RevisionsRequest request = mBucket.getRevisions(key, history);

        assertEquals(2, history.receivedRevisions);
        assertEquals("Title 1", history.notes.get(0).getTitle());
        assertTrue(history.complete);
    }

    public void testObjectHistoryWithInstance() throws Exception {
        String key = "fake-history";
        int version = 5, revisions = version-1;
        // put a fake ghost in the ghost store
        Map<String,Object> properties = new HashMap<String,Object>(2);
        properties.put("title", "Hello World");
        properties.put("content", "Lorem ipsum");
        Ghost ghost = new Ghost(key, version, properties);

        // send the fake ghost in
        mBucket.addObjectWithGhost(ghost);
        
        // fully initialize the note
        Note note = mBucket.get(key);

        RevisionsReceiver history = new RevisionsReceiver();
        ChannelProvider.RevisionsRequest request = note.getRevisions(history);

        assertEquals(revisions, history.receivedRevisions);
        assertEquals("Title 1", history.notes.get(0).getTitle());
        assertTrue(history.complete);

    }


    class RevisionsReceiver implements Bucket.RevisionsRequestCallbacks<Note> {

        public int receivedRevisions = 0;
        public List<Note> notes = new ArrayList<Note>();
        public boolean complete = false;

        @Override
        public void onComplete(){
            complete = true;
        }

        @Override
        public void onRevision(String key, int version, Note note){
            notes.add(version-1, note);
            receivedRevisions ++;
        }

        @Override
        public void onError(Throwable error){
            
        }
    }
}
