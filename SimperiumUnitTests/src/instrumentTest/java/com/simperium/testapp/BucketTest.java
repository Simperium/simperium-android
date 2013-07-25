package com.simperium.testapp;

import static com.simperium.testapp.TestHelpers.*;

import com.simperium.client.Change;
import com.simperium.client.RemoteChange;
import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.ObjectCache;
import com.simperium.client.GhostStoreProvider;
import com.simperium.client.User;
import com.simperium.storage.MemoryStore;

import com.simperium.util.Uuid;
import com.simperium.util.Logger;

import com.simperium.testapp.models.Note;
import com.simperium.testapp.mock.MockChannel;
import com.simperium.testapp.mock.MockCache;
import com.simperium.testapp.mock.MockGhostStore;

import org.json.JSONArray;

public class BucketTest extends SimperiumTest {

    public static final String TAG="SimperiumTest";
    private Bucket<Note> mBucket;
    private BucketSchema<Note> mSchema;
    private User mUser;
    private GhostStoreProvider mGhostStore;
    private Bucket.ChannelProvider<Note> mChannel;

    private static String BUCKET_NAME="local-notes";

    protected void setUp() throws Exception {
        super.setUp();

        mUser = makeUser();

        mSchema = new Note.Schema();
        MemoryStore storage = new MemoryStore();
        mGhostStore = new MockGhostStore();
        ObjectCache<Note> cache = new ObjectCache<Note>(new MockCache<Note>());
        mBucket = new Bucket<Note>(BUCKET_NAME, mSchema, mUser, storage.createStore(BUCKET_NAME, mSchema), mGhostStore, cache);
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

}
