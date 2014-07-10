package com.simperium.android;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.simperium.client.Bucket;
import com.simperium.client.Change;
import com.simperium.client.Channel;
import com.simperium.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

public class QueueSerializer implements Channel.Serializer {

    public static final String TAG = "Simperium.QueueSerializer";

    static public final String TABLE_NAME      = "queue";
    static public final String FIELD_BUCKET    = "bucket";
    static public final String FIELD_KEY       = "key";
    static public final String FIELD_STATUS    = "status";
    static public final String FIELD_OPERATION = "operation";
    static public final String FIELD_CCID      = "ccid";

    protected SQLiteDatabase mDatabase;

    private enum Status {
        QUEUED("Q"), PENDING("P");

        protected String mStatus;

        Status(String status){
            mStatus = status;
        }

        @Override
        public String toString(){
            return mStatus;
        }
    }

    public QueueSerializer(SQLiteDatabase database) {
        mDatabase = database;
        prepare();
    }

    private void prepare(){
        // create the table for the database
        // bucket, key, status, operation and diff
        mDatabase.execSQL(String.format("CREATE TABLE IF NOT EXISTS %s (%s, %s, %s, %s, %s)",
            TABLE_NAME, FIELD_BUCKET, FIELD_KEY, FIELD_STATUS, FIELD_OPERATION, FIELD_CCID));

        // searching by bucket and key
        mDatabase.execSQL(String.format("CREATE INDEX IF NOT EXISTS queue_object_key ON %s (%s, %s)",
            TABLE_NAME, FIELD_BUCKET, FIELD_KEY));

        // searching by bucket and status
        mDatabase.execSQL(String.format("CREATE INDEX IF NOT EXISTS queue_status ON %s (%s, %s)",
            TABLE_NAME, FIELD_BUCKET, FIELD_STATUS));

        // searching by bucket and status
        mDatabase.execSQL(String.format("CREATE INDEX IF NOT EXISTS queue_ccid ON %s (%s, %s)",
            TABLE_NAME, FIELD_BUCKET, FIELD_CCID));
    }

    private static final String QUERY_CLAUSE = String.format("%s = ?", FIELD_BUCKET);
    @Override
    public Channel.SerializedQueue restore(Bucket bucket) {
        Channel.SerializedQueue queue = new Channel.SerializedQueue();
        // public Cursor query (String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy)
        String[] args = new String[]{ bucket.getName() };
        Cursor items = mDatabase.query(TABLE_NAME, null, QUERY_CLAUSE, args, null, null, null);

        try {

            int bucketColumn = items.getColumnIndexOrThrow(FIELD_BUCKET);
            int keyColumn = items.getColumnIndexOrThrow(FIELD_KEY);
            int statusColumn = items.getColumnIndexOrThrow(FIELD_STATUS);
            int operationColumn = items.getColumnIndexOrThrow(FIELD_OPERATION);
            int ccidColumn = items.getColumnIndexOrThrow(FIELD_CCID);

            String key;
            String operation;
            String status;

            while(items.moveToNext()){
                key = items.getString(keyColumn);
                operation = items.getString(operationColumn);
                status = items.getString(statusColumn);

                Change change = Change.buildChange(operation, items.getString(ccidColumn),
                    items.getString(bucketColumn), key);

                if (status.equals(Status.QUEUED.toString())) {
                    queue.queued.add(change);
                } else if(status.equals(Status.PENDING.toString())) {
                    queue.pending.put(key, change);
                }
            }

        } catch (IllegalArgumentException e) {
            Logger.log(TAG, "Could not restore queue, invalid table columns", e);
            return null;
        }

        return queue;
    }

    @Override
    public void reset(Bucket bucket) {
        // delete everything from the queue for this bucket
    }

    @Override
    public void onQueueChange(Change change) {
        // insert the record into the db
        insertState(Status.QUEUED, change);
    }

    @Override
    public void onDequeueChange(Change change) {
        // change will not be sent, remove queued status
        removeState(Status.QUEUED, change);
    }

    @Override
    public void onSendChange(Change change) {
        // change was sent, mark as pending
        updateState(Status.PENDING, change);
    }

    @Override
    public void onAcknowledgeChange(Change change) {
        // change was acknowledge, remove pending status
        removeState(Status.PENDING, change);
    }

    private void insertState(Status status, Change change) {

        ContentValues values = new ContentValues(6);
        values.put(FIELD_BUCKET, change.getBucketName());
        values.put(FIELD_KEY, change.getKey());
        values.put(FIELD_STATUS, status.toString());
        values.put(FIELD_OPERATION, change.getOperation());
        values.put(FIELD_CCID, change.getChangeId());

        try {
            mDatabase.insertOrThrow(TABLE_NAME, null, values);
        } catch (SQLException e) {
            Logger.log(TAG, "Unable to insert status change", e);
        }
    }

    static public final String UPDATE_CLAUSE = String.format("%s = ? AND %s = ?",
        FIELD_BUCKET, FIELD_CCID);

    private void updateState(Status status, Change change) {
        ContentValues values = new ContentValues(1);
        String[] args = new String[] { change.getBucketName(), change.getChangeId() };
        values.put(FIELD_STATUS, status.toString());
        mDatabase.update(TABLE_NAME, values, UPDATE_CLAUSE, args);
    }

    static private final String REMOVE_CLAUSE = String.format("%s = ? AND %s = ?",
        FIELD_BUCKET, FIELD_CCID );

    private void removeState(Status status, Change change) {
        String[] conditions = new String[] { change.getBucketName(), change.getChangeId() };
        mDatabase.delete(TABLE_NAME, REMOVE_CLAUSE, conditions);
    }

}