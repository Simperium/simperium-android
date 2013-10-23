package com.simperium;

import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;

import static android.test.MoreAsserts.*;
import static com.simperium.TestHelpers.*;

import com.simperium.android.LoginActivity;
import com.simperium.android.QueueSerializer;
import com.simperium.client.Change;
import com.simperium.client.Channel;
import com.simperium.client.Channel.SerializedQueue;
import com.simperium.client.Bucket;
import com.simperium.client.BucketObject;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;

import com.simperium.test.MockBucket;

import java.util.Map;
import java.util.HashMap;

public class QueueSerializerTest extends ActivityInstrumentationTestCase2<LoginActivity> {

    protected QueueSerializer mSerializer;
    protected SQLiteDatabase mDatabase;

    protected Bucket<BucketObject> mBucket;

    public QueueSerializerTest() {
        super(LoginActivity.class);
    }

    protected void setUp() throws Exception {
        mDatabase = getActivity().openOrCreateDatabase("queue-test", 0, null);
        mSerializer = new QueueSerializer(mDatabase);
        BucketObject.Schema schema = new BucketObject.Schema("mock-bucket");
         mBucket = MockBucket.buildBucket(schema);
    }

    public void testDatabaseSetup() throws Exception {
        assertTableExists(mDatabase, QueueSerializer.TABLE_NAME);
    }

    public void testQueueChange() throws Exception {
        BucketObject object = mBucket.newObject();
        object.setProperty("title", "Hola Mundo");

        Change change = new Change(Change.OPERATION_MODIFY, object);

        mSerializer.onQueueChange(change);

        SerializedQueue queue = mSerializer.restore(mBucket);
        assertEquals(1, queue.queued.size());

        mSerializer.onDequeueChange(change);

        queue = mSerializer.restore(mBucket);
        assertEquals(0, queue.queued.size());
    }

    public void testUpdateChange() throws Exception {
        BucketObject object = mBucket.newObject();
        object.setProperty("title", "Hola Mundo");

        Change change = new Change(Change.OPERATION_REMOVE, object);
        mSerializer.onQueueChange(change);

        SerializedQueue queue = mSerializer.restore(mBucket);
        assertEquals(1, queue.queued.size());

        mSerializer.onSendChange(change);

        queue = mSerializer.restore(mBucket);
        assertEquals(1, queue.pending.size());
        assertEquals(0, queue.queued.size());

    }

    public static void assertTableExists(SQLiteDatabase database, String tableName) {
        Cursor cursor = database.query("sqlite_master", new String[]{"name"}, "type=? AND name=?", new String[]{"table", tableName}, "name", null, null, null);
        assertEquals(String.format("Table %s does not exist in %s", tableName, database), 1, cursor.getCount());
    }

}
