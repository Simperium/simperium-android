package com.simperium.storage;


import com.simperium.client.BucketSchema;
import com.simperium.client.Syncable;
import com.simperium.client.Channel;
import com.simperium.client.BucketObjectMissingException;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.content.ContentValues;

import java.util.HashMap;

import org.json.JSONObject;
import org.json.JSONException;

public class PersistentStore implements StorageProvider {
    public static final String OBJECTS_TABLE="objects";

    private SQLiteDatabase database;

    public PersistentStore(SQLiteDatabase database){
        this.database = database;
        configure();
    }

    public Cursor queryObject(String bucketName, String key){
        return database.query(OBJECTS_TABLE, null, "bucket=? AND key=?", new String[]{bucketName, key}, null, null, null, "1");
    }

    @Override
    public <T extends Syncable> BucketStore<T> createStore(String bucketName, BucketSchema<T> schema){
        return new DataStore<T>(bucketName, schema);
    }

    private class DataStore<T extends Syncable> implements BucketStore<T> {

        private BucketSchema<T> schema;
        private String bucketName;

        DataStore(String bucketName, BucketSchema<T> schema){
            this.schema = schema;
            this.bucketName = bucketName;
        }

        /**
         * Add/Update the given object
         */
        @Override
        public void save(T object){
            String key = object.getSimperiumKey();
            ContentValues values = new ContentValues();
            values.put("bucket", bucketName);
            values.put("key", key);
            values.put("data", Channel.serializeJSON(object.getDiffableValue()).toString());
            Cursor cursor = queryObject(bucketName, key);
            if (cursor.getCount() == 0) {
                database.insert(OBJECTS_TABLE, null, values);
            } else {
                database.update(OBJECTS_TABLE, values, "bucket=? AND key=?", new String[]{bucketName, key});
            }
        }

        /**
         * Remove the given object from the storage
         */
        @Override
        public void delete(T object){
            String key = object.getSimperiumKey();
            database.delete(OBJECTS_TABLE, "bucket=? AND key=?", new String[]{bucketName, key});
        }

        /**
         * Delete all objects from storage
         */
        @Override
        public void reset(){
            database.delete(OBJECTS_TABLE, "bucket=?", new String[]{bucketName});
        }

        /**
         * Get an object with the given key
         */
        @Override
        public T get(String key) throws BucketObjectMissingException {
            BucketCursor<T> cursor = buildCursor(schema, queryObject(bucketName, key));
            if (cursor.getCount() == 0) {
                throw(new BucketObjectMissingException());
            } else {
                cursor.moveToFirst();                
                return cursor.getObject();
            }
        }

        /**
         * All objects, returns a cursor for the given bucket
         */
        @Override
        public BucketCursor<T> all(){
            return buildCursor(schema, database.query(OBJECTS_TABLE, null, "bucket=?", new String[]{bucketName}, null, null, null, null));
        }

    }

    private class ObjectCursor<T extends Syncable> extends CursorWrapper implements BucketCursor<T> {
        
        private BucketSchema<T> schema;

        ObjectCursor(BucketSchema<T> schema, Cursor cursor){
            super(cursor);
            this.schema = schema;
        }

        public T getObject(){
            String key = getString(1);
            try {
                JSONObject data = new JSONObject(getString(2));
                return schema.build(key, Channel.convertJSON(data));
            } catch (org.json.JSONException e) {
                return schema.build(key, new HashMap<String,Object>());
            }
        }
    }

    private <T extends Syncable> BucketCursor<T> buildCursor(BucketSchema<T> schema, Cursor cursor){
        return new ObjectCursor<T>(schema, cursor);
    }
    
    private void configure(){
        // create and validate the tables we'll be using for the datastore
        configureObjects();
    }
        
    private void configureIndexes(){
        Cursor tableInfo = tableInfo("indexes");
        if (tableInfo.getCount() == 0) {
            // create the table
            database.execSQL("CREATE TABLE indexes (bucket, key, value)");
        }
    }

    private void configureObjects(){
        Cursor tableInfo = tableInfo("objects");
        if (tableInfo.getCount() == 0) {
            database.execSQL("CREATE TABLE objects (bucket, key, data)");
        }
    }

    private Cursor tableInfo(String tableName){
        return database.rawQuery(String.format("PRAGMA table_info(%s)", tableName), null);
    }

}