package com.simperium.client;

import com.simperium.client.Bucket;
import com.simperium.client.BucketNameInvalid;
import com.simperium.client.BucketObjectNameInvalid;
import com.simperium.client.BucketSchema;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.User;

import com.simperium.models.Note;

import com.simperium.storage.MemoryStore;

import com.simperium.test.MockCache;
import com.simperium.test.MockChannel;
import com.simperium.test.MockGhostStore;
import com.simperium.test.MockExecutor;

import com.simperium.util.RemoteChangesUtil;

import org.json.JSONObject;

import static com.simperium.TestHelpers.makeUser;

import junit.framework.TestCase;

public class BucketTest extends TestCase {

    public static final String TAG="SimperiumTest";
    private Bucket<Note> mBucket;
    private BucketSchema<Note> mSchema;
    private User mUser;
    private GhostStorageProvider mGhostStore;
    private Bucket.Channel mChannel;

    private static String BUCKET_NAME="local-notes";

    protected void setUp() throws Exception {
        super.setUp();

        mUser = makeUser();

        mSchema = new Note.Schema();
        MemoryStore storage = new MemoryStore();
        mGhostStore = new MockGhostStore();
        MockCache<Note> cache = new MockCache<Note>();
        mBucket = new Bucket<Note>(MockExecutor.immediate(), BUCKET_NAME, mSchema, mUser, storage.createStore(BUCKET_NAME, mSchema), mGhostStore, cache);
        mChannel = new MockChannel(mBucket);
        mBucket.setChannel(mChannel);
        mBucket.start();
    }

    public void testBucketName()
    throws Exception {
        assertEquals(BUCKET_NAME, mBucket.getName());
        assertEquals(mSchema.getRemoteName(), mBucket.getRemoteName());
    }

    public void testBuildObject()
    throws Exception {
        Note note = mBucket.newObject();
        assertTrue(note.isNew());
    }

    public void testSaveObject()
    throws Exception {
        Note note = mBucket.newObject();
        note.setTitle("Hello World");
        assertTrue(note.isNew());
        note.save();

        assertFalse(note.isNew());
    }

    public void testInvalidObjectNameThrowsException()
    throws Exception {
        BucketObjectNameInvalid exception = null;
        try {
            mBucket.newObject("bad name");
        } catch (BucketObjectNameInvalid e) {
            exception = e;
        }

        assertNotNull(exception);
    }

    public void testNullObjectNameThrowsException()
    throws Exception {
        BucketObjectNameInvalid exception = null;
        try {
            mBucket.newObject(null);
        } catch (BucketObjectNameInvalid e) {
            exception = e;
        }

        assertNotNull(exception);
    }

    public void testTrimObjectNameWhiteSpace()
    throws Exception {
        BucketObjectNameInvalid exception = null;
        Note note = mBucket.newObject("  whitespace ");

        assertEquals("whitespace", note.getSimperiumKey());

    }

    public void testValidateBucketName()
    throws Exception {
        BucketNameInvalid exception = null;
        try {
            Bucket.validateBucketName("hello world");
        } catch (BucketNameInvalid e) {
            exception = e;
        }

        assertNotNull(exception);
    }

    public void testValidateNullBucketName()
    throws Exception {
        BucketNameInvalid exception = null;
        try {
            Bucket.validateBucketName(null);
        } catch (BucketNameInvalid e) {
            exception = e;
        }

        assertNotNull(exception);
    }

    public void testApplyRemoteChange()
    throws Exception {

        Note note = mBucket.newObject();

        note.setContent("Line 1\n");
        note.save();

        // create a 3rd party modification
        JSONObject external = new JSONObject(note.getDiffableValue().toString());
        external.put("content", "Line 1\nLine 2\n");

        note.setTitle("Lol");

        // build remote change based on 3rd party modification
        RemoteChange change = RemoteChangesUtil.buildRemoteChange(note, external);

        mBucket.applyRemoteChange(change);

        assertEquals("Line 1\nLine 2\n", note.getContent());

    }

    public void testMergeLocalChanges()
    throws Exception {

        Note note = mBucket.newObject();

        note.setContent("Line 1\n");
        note.save();

        // make a local modification before remote change comes in
        note.setContent("Line 1\nLine 3\n");

        // create a 3rd party modification
        JSONObject external = new JSONObject(note.getDiffableValue().toString());
        external.put("content", "Line 1\nLine 2\n");

        // build remote change based on 3rd party modification
        RemoteChange change = RemoteChangesUtil.buildRemoteChange(note, external);

        mBucket.applyRemoteChange(change);

        assertEquals("Line 1\nLine 2\nLine 3\n", note.getContent());

    }

    /**
     * Perform a 3-way merge when an object is redownloaded and has local
     * modifications.
     * 
     * See issue https://github.com/Simperium/simperium-android/issues/82
     */
    public void testRedownloadWithLocalModifications()
    throws Exception {

        Note note = mBucket.newObject();

        note.setContent("Line 1\nLine 2\nLine 3");
        note.save();

        // modify note locally
        note.setContent("Line 0\nLine 1\nLine 2\nLine 3");

        // Simulate channel redownloading a note
        JSONObject data = new JSONObject("{\"content\":\"Line 1\nLine 2\nLine 3\nLine 4\"}");
        Ghost ghost = new Ghost(note.getSimperiumKey(), 1000, data);

        mBucket.updateGhost(ghost, null);

        assertEquals("Line 0\nLine 1\nLine 2\nLine 3\nLine 4", note.getContent());

    }

}
