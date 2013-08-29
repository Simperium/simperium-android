package com.simperium.testapp;

import static com.simperium.testapp.TestHelpers.*;

import com.simperium.client.Change;
import com.simperium.client.RemoteChange;
import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.User;
import com.simperium.storage.MemoryStore;

import com.simperium.util.Uuid;
import com.simperium.util.Logger;

import com.simperium.testapp.models.Note;
import com.simperium.testapp.mock.MockChannel;
import com.simperium.testapp.mock.MockCache;
import com.simperium.testapp.mock.MockGhostStore;
import com.simperium.testapp.mock.MockSyncService;

import org.json.JSONArray;

public class BucketTest extends BaseSimperiumTest {

    public static final String TAG="SimperiumTest";
    private Bucket<Note> mBucket;
    private BucketSchema<Note> mSchema;
    private User mUser;
    private GhostStorageProvider mGhostStore;
    private Bucket.Channel<Note> mChannel;

    private static String BUCKET_NAME="local-notes";

    protected void setUp() throws Exception {
        super.setUp();

        mUser = makeUser();

        mSchema = new Note.Schema();
        MemoryStore storage = new MemoryStore();
        mGhostStore = new MockGhostStore();
        MockCache<Note> cache = new MockCache<Note>();
        mBucket = new Bucket<Note>(MockSyncService.service(), BUCKET_NAME, mSchema, mUser, storage.createStore(BUCKET_NAME, mSchema), mGhostStore, cache);
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
