package com.simperium.client;

import com.simperium.models.Note;

import com.simperium.storage.MemoryStore;

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
        mBucket = new Bucket<Note>(MockExecutor.immediate(), BUCKET_NAME, mSchema, mUser, storage.createStore(BUCKET_NAME, mSchema), mGhostStore);
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

    public void testMergeLocalChangesWithUpdatedGhost()
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
        Ghost ghost = change.apply(note.getGhost());

        mBucket.updateGhost(ghost, null);

        assertEquals("Line 1\nLine 2\nLine 3\n", note.getContent());
    }

    /**
     * If two different notes are both saved and then both deleted, they should both be missing from persistent store
     * but present in the backup store.
     *
     * This also checks that the backup store isn't being cleared before the Channel has a chance to retrieve a backup
     * object and send it.
     */
    public void testConsecutiveSaveDeleteObjects() {
        Note note1 = mBucket.newObject();
        note1.setTitle("Hello World");

        Note note2 = mBucket.newObject();
        note2.setTitle("Hello Again World");

        note1.save();
        note2.save();

        note1.delete();
        note2.delete();

        // Test retrieving notes from persistent store
        BucketObjectMissingException note1MissingException = null;
        BucketObjectMissingException note2MissingException = null;
        try {
            mBucket.getObject(note1.getSimperiumKey());
        } catch (BucketObjectMissingException e) {
            note1MissingException = e;
        }
        try {
            mBucket.getObject(note2.getSimperiumKey());
        } catch (BucketObjectMissingException e) {
            note2MissingException = e;
        }
        // Retrieval from persistent store should fail
        assertNotNull(note1MissingException);
        assertNotNull(note2MissingException);

        // Test retrieving notes from backup store
        note1MissingException = null;
        note2MissingException = null;
        try {
            mBucket.getObjectOrBackup(note1.getSimperiumKey());
        } catch (BucketObjectMissingException e) {
            note1MissingException = e;
        }
        try {
            mBucket.getObjectOrBackup(note2.getSimperiumKey());
        } catch (BucketObjectMissingException e) {
            note2MissingException = e;
        }
        // Retrieval from backup store should succeed
        assertNull(note1MissingException);
        assertNull(note2MissingException);
    }

}
