package com.simperium;

import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.BucketSchema;
import com.simperium.client.Channel;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.User;
import com.simperium.models.Note;
import com.simperium.storage.MemoryStore;
import com.simperium.test.MockChannelListener;
import com.simperium.test.MockChannelSerializer;
import com.simperium.test.MockGhostStore;
import com.simperium.util.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static android.test.MoreAsserts.assertMatchesRegex;
import static com.simperium.TestHelpers.makeUser;
import static com.simperium.TestHelpers.waitUntil;

public class ConcurrencyTest extends BaseSimperiumTest {
    public static final String TAG = "SimperiumTest";

    private static String BUCKET_NAME = "concurrency-test";
    public static String SESSION_ID = "SESSION-ID";
    public static String APP_ID = "APP_ID";

    private Bucket<Note> mBucket;
    private Channel mChannel;
    private MockChannelSerializer mChannelSerializer = new MockChannelSerializer();
    final private MockChannelListener mListener = new MockChannelListener();

    private BucketSchema<Note> mSchema;
    private User mUser;
    private MemoryStore mStorage;
    private GhostStorageProvider mGhostStore;

    protected User.Status mAuthStatus;

    private ThreadPoolExecutor mExecutor;

    /**
     * Build Bucket and Channel instances using a multi-threaded Executor
     */
    protected void setUp() throws Exception {
        super.setUp();

        // Mimic AndroidClient's Executor setup
        int threads = Runtime.getRuntime().availableProcessors();
        if (threads > 1) {
            threads -= 1;
        }

        mExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads);

        mUser = makeUser();
        mSchema = new Note.Schema();
        mStorage = new MemoryStore();
        mGhostStore = new MockGhostStore();
        mBucket = new Bucket<>(mExecutor, BUCKET_NAME, mSchema, mUser, mStorage.createStore(BUCKET_NAME, mSchema), mGhostStore);

        mChannel = new Channel(mExecutor, APP_ID, SESSION_ID, mBucket, mChannelSerializer, mListener);
        mBucket.setChannel(mChannel);

        mBucket.getUser().setStatusChangeListener(new User.StatusChangeListener(){
            @Override
            public void onUserStatusChange(User.Status status){
                mAuthStatus = status;
            }
        });

    }

    protected void tearDown() throws Exception {
        mChannel.stop();
        mChannel.reset();
        super.tearDown();
    }

    /**
     * Same as ChannelTest.testSendFinalModificationBeforeDeleteOperation() but with concurrency enabled.
     *
     * See https://github.com/Simperium/simperium-android/issues/159
     * Ensures that the channel is able to send out final modification operations for an object even if
     * the object has since been removed from the local persistent store.
     */
    public void testSendFinalModificationBeforeDeleteOperationConcurrent() throws Exception {

        startWithEmptyIndex();

        String key = "test-modify-before-delete-object";
        Note note = mBucket.newObject(key);
        note.setTitle("Bonjour le monde!");

        note.save();

        // Queue a deletion and remove the object from the local persistent store before the modification has been sent
        note.delete();

        clearMessages();
        waitForMessage();

        // Message should be a change message "c:{}"
        assertMatchesRegex("^c:\\{.*\\}$", mListener.lastMessage.toString());

        // First change has been sent and it's a modification
        assertEquals(1, mChannelSerializer.queue.pending.size());
        assertTrue(mChannelSerializer.queue.pending.get(key).isModifyOperation());

        // Second change is queued and it's a deletion
        assertEquals(1, mChannelSerializer.queue.queued.size());
        assertTrue(mChannelSerializer.queue.queued.get(0).isRemoveOperation());

    }

    /**
     * Same as BucketTest.testConsecutiveSaveDeleteObjects() but with concurrency enabled.
     *
     * If two different notes are both saved and then both deleted, they should both be missing from persistent store
     * but present in the backup store.
     *
     * This also checks that the backup store isn't being cleared before the Channel has a chance to retrieve a backup
     * object and send it.
     */
    public void testConsecutiveSaveDeleteObjectsConcurrent() throws InterruptedException {
        Note note1 = mBucket.newObject();
        note1.setTitle("Hello World");

        Note note2 = mBucket.newObject();
        note2.setTitle("Hello Again World");

        note1.save();
        note2.save();

        note1.delete();
        note2.delete();

        // Allow the save and delete tasks to finish before querying storage
        waitForExecutorCompletedTasks(4);

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


    /**
     * Gets the channel into a started state
     */
    protected void start(){
        mChannel.onConnect();
        mChannel.start();
        // send auth success message
        mChannel.receiveMessage("auth:user@example.com");
    }

    protected void startWithEmptyIndex()
            throws Exception {
        start();
        sendEmptyIndex();
    }

    protected void startWithIndex(Map<String,String> objects)
            throws Exception {
        startWithIndex("mock-cv", objects);
    }

    protected void startWithIndex(String cv, Map<String,String> objects)
            throws Exception {
        start();
        sendIndex(cv, objects);
    }

    /**
     * Simulates a brand new bucket with and empty index
     */
    protected void sendEmptyIndex()
            throws Exception {
        sendMessage("i:{\"index\":[]}");
        waitForIndex();
    }

    protected void sendIndex(String cv, Map<String,String> objects)
            throws Exception {
        JSONObject index = new JSONObject();
        String period = ".";
        index.put("current", cv);

        JSONArray versions = new JSONArray();
        index.put("index", versions);

        for(Map.Entry<String,String> entry : objects.entrySet()) {
            String key = entry.getKey();
            int dot = key.indexOf(period);
            String id = key.substring(0, dot);
            int version = Integer.parseInt(key.substring(dot+1));
            JSONObject versionData = new JSONObject();
            versionData.put("v", version);
            versionData.put("id", id);

            versions.put(versionData);
        }

        mListener.indexVersions = versions;
        mListener.indexData = objects;
        sendMessage(String.format("i:%s", index));
        waitForIndex();
    }

    protected void sendMessage(String message){
        mChannel.receiveMessage(message);
    }

    protected Channel.MessageEvent waitForMessage() throws InterruptedException {
        return waitForMessage(2000);
    }

    /**
     * Wait until a message received. More than likely clearMessages() should
     * be called before waitForMessage()
     */
    protected Channel.MessageEvent waitForMessage(int waitFor) throws InterruptedException {

        NewMessageFlagger flagger = new NewMessageFlagger();

        waitUntil(flagger, "No message received", waitFor);

        return flagger.message;

    }

    /**
     * Empties the list of received messages and sets last message to null
     */
    protected void clearMessages(){
        mListener.clearMessages();
    }

    protected void waitForIndex()
            throws InterruptedException {
        waitUntil(new TestHelpers.Flag(){
            @Override
            public boolean isComplete(){
                return mChannel.haveCompleteIndex();
            }
        }, "Index never received", 5000);
    }

    protected void waitForExecutorCompletedTasks(final int completedTasks) throws InterruptedException {
        waitUntil(new TestHelpers.Flag(){
            @Override
            public boolean isComplete(){
                return (mExecutor.getCompletedTaskCount() == completedTasks);
            }
        }, "Completed task amount never reached", 5000);
    }

    private class NewMessageFlagger implements TestHelpers.Flag {

        Channel.MessageEvent message;

        @Override
        public boolean isComplete() {

            message = mListener.lastMessage;

            return mListener.lastMessage != null;

        }

    }
}
