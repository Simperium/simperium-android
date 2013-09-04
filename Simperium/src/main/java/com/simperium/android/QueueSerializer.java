package com.simperium.android;

import com.simperium.client.Syncable;
import com.simperium.client.Channel;
import com.simperium.client.Change;
import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;

import com.simperium.util.Logger;

import org.json.JSONObject;
import org.json.JSONException;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.database.SQLException;
import android.content.ContentValues;

import java.util.Map;

public class QueueSerializer<T extends Syncable> implements Channel.Serializer<T> {

    public static final String TAG = "Simperium.QueueSerializer";

    static public final String TABLE_NAME      = "queue";
    static public final String FIELD_BUCKET    = "bucket";
    static public final String FIELD_KEY       = "key";
    static public final String FIELD_STATUS    = "status";
    static public final String FIELD_VERSION   = "version";
    static public final String FIELD_OPERATION = "operation";
    static public final String FIELD_ORIGIN    = "origin";
    static public final String FIELD_TARGET    = "target";
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
        // bucket, key, status, version number, operation and diff
        mDatabase.execSQL(String.format("CREATE TABLE IF NOT EXISTS %s (%s, %s, %s, %s, %s, %s, %s, %s)",
            TABLE_NAME, FIELD_BUCKET, FIELD_KEY, FIELD_STATUS, FIELD_VERSION, FIELD_OPERATION,
            FIELD_ORIGIN, FIELD_TARGET, FIELD_CCID));

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
    public Channel.SerializedQueue<T> restore(Bucket<T> bucket) {
        Channel.SerializedQueue queue = new Channel.SerializedQueue();
        // public Cursor query (String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy)
        String[] args = new String[]{ bucket.getName() };
        Cursor items = mDatabase.query(TABLE_NAME, null, QUERY_CLAUSE, args, null, null, null);

        try {

            int bucketColumn = items.getColumnIndexOrThrow(FIELD_BUCKET);
            int keyColumn = items.getColumnIndexOrThrow(FIELD_KEY);
            int statusColumn = items.getColumnIndexOrThrow(FIELD_STATUS);
            int versionColumn = items.getColumnIndexOrThrow(FIELD_VERSION);
            int operationColumn = items.getColumnIndexOrThrow(FIELD_OPERATION);
            int originColumn = items.getColumnIndexOrThrow(FIELD_ORIGIN);
            int targetColumn = items.getColumnIndexOrThrow(FIELD_TARGET);
            int ccidColumn = items.getColumnIndexOrThrow(FIELD_CCID);

            String key = null;
            String operation = null;
            String status = null;
            Map<String,Object> origin = null;
            Map<String,Object> target = null;

            while(items.moveToNext()){
                try {
                    key = items.getString(keyColumn);
                    operation = items.getString(operationColumn);
                    status = items.getString(statusColumn);

                    if (operation.equals(Change.OPERATION_MODIFY)) {
                        origin = Channel.convertJSON(new JSONObject(items.getString(originColumn)));
                        target = Channel.convertJSON(new JSONObject(items.getString(targetColumn)));
                    } else {
                        origin = null;
                        target = null;
                    }

                    Change change = Change.buildChange(operation, items.getString(ccidColumn),
                        items.getString(bucketColumn), key, items.getInt(versionColumn), origin, target);

                    if (status.equals(Status.QUEUED.toString())) {
                        queue.queued.add(change);
                    } else if(status.equals(Status.PENDING.toString())) {
                        queue.pending.put(key, change);
                    }
                } catch (JSONException e) {
                    Logger.log(TAG, String.format("Failed to deserialize item", e));
                }
            }

        } catch (IllegalArgumentException e) {
            Logger.log(TAG, "Could not restore queue, invalid table columns", e);
            return null;
        }

        return queue;
    }

    @Override
    public void reset(Bucket<T> bucket) {
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
        values.put(FIELD_VERSION, change.getVersion());
        values.put(FIELD_OPERATION, change.getOperation());
        values.put(FIELD_CCID, change.getChangeId());
        if (change.isModifyOperation()) {
            values.put(FIELD_ORIGIN, Channel.serializeJSON(change.getOrigin()).toString());
            values.put(FIELD_TARGET, Channel.serializeJSON(change.getTarget()).toString());
        }

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