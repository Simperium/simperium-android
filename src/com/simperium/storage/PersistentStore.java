package com.simperium.storage;

import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.BucketSchema.Index;
import com.simperium.client.Syncable;
import com.simperium.client.Channel;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.Query;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.content.ContentValues;
import android.os.CancellationSignal;

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import org.json.JSONObject;
import org.json.JSONException;

import android.util.Log;

public class PersistentStore implements StorageProvider {
    public static final String TAG="Simperium.Store";
    public static final String OBJECTS_TABLE="objects";
    public static final String INDEXES_TABLE="indexes";

    private SQLiteDatabase database;

    public PersistentStore(SQLiteDatabase database){
        this.database = database;
        configure();
    }

    public Cursor queryObject(String bucketName, String key){
        return database.query(OBJECTS_TABLE, new String[]{"objects.rowid AS _id", "objects.bucket", "objects.key as `object_key`", "objects.data as `object_data`"}, "bucket=? AND key=?", new String[]{bucketName, key}, null, null, null, "1");
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
        public void save(T object, List<Index> indexes){
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
            index(object, indexes);
        }

        /**
         * Remove the given object from the storage
         */
        @Override
        public void delete(T object){
            String key = object.getSimperiumKey();
            database.delete(OBJECTS_TABLE, "bucket=? AND key=?", new String[]{bucketName, key});
            deleteIndexes(object);
        }

        /**
         * Delete all objects from storage
         */
        @Override
        public void reset(){
            database.delete(OBJECTS_TABLE, "bucket=?", new String[]{bucketName});
            deleteAllIndexes();
        }

        /**
         * Get an object with the given key
         */
        @Override
        public T get(String key) throws BucketObjectMissingException {
            Bucket.ObjectCursor<T> cursor = buildCursor(schema, queryObject(bucketName, key));
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
        public Bucket.ObjectCursor<T> all(CancellationSignal cancelSignal){
            return buildCursor(schema, database.query(false, OBJECTS_TABLE,
                    new String[]{"objects.rowid AS _id", "objects.bucket", "objects.key as `object_key`", "objects.data as `object_data`"},
                    "bucket=?", new String[]{bucketName}, null, null, null, null, cancelSignal));
        }

        /**
         * Search the datastore using the given Query
         * 
         * This is really ugly, please refactor and use StringBuilder too
         */
        @Override
        public Bucket.ObjectCursor<T> search(Query<T> query, CancellationSignal cancelSignal){
            // turn comparators into where statements, each comparator joins
            Iterator<Query.Comparator> conditions = query.getConditions().iterator();
            Iterator<Query.Sorter> sorters = query.getSorters().iterator();
            String selection = "SELECT DISTINCT objects.rowid AS _id, objects.bucket, objects.key as `object_key`, objects.data as `object_data` FROM objects";
            String filters = "";
            String where = "WHERE objects.bucket = ?";
            List<String> replacements = new ArrayList<String>();
            replacements.add(bucketName);
            List<String> names = new ArrayList<String>();
            int i = 0;

            List<String> sortKeys = new ArrayList<String>();
            Map<String,String> includedSorts = new HashMap<String,String>();
            while(sorters.hasNext()){
                sortKeys.add(sorters.next().getKey());
            }
            
            while(conditions.hasNext()){
                Query.Comparator condition = conditions.next();
                String key = condition.getKey();
                if (sortKeys.contains(condition.getKey())) {
                    includedSorts.put(key, String.format("i%d", i));
                }
                names.add(condition.getKey());
                filters = String.format("%s LEFT JOIN indexes AS i%d ON objects.bucket = i%d.bucket AND objects.key = i%d.key AND i%d.name=?", filters, i, i, i, i);
                Object subject = condition.getSubject();
                String null_condition = condition.includesNull() ? String.format(" i%d.value IS NULL OR", i) : String.format(" i%d.value IS NOT NULL AND", i);
                where = String.format("%s AND ( %s i%d.value %s ", where, null_condition, i, condition.getComparisonType());
                if (subject instanceof Float) {
                    where = String.format("%s %f)", where, (Float)subject);
                } else if (subject instanceof Integer){
                    where = String.format("%s %d)", where, (Integer)subject);
                } else if (subject instanceof Boolean){
                    where = String.format("%s %d)", where, ((Boolean)subject ? 1 : 0));
                } else {
                    where = String.format("%s ?)", where);
                    replacements.add(subject.toString());
                }
                i++;
            }

            String order = "ORDER BY";
            if (query.getSorters().size() > 0){
                sorters = query.getSorters().iterator();
                while(sorters.hasNext()){
                    if (!order.equals("ORDER BY")) {
                        order = String.format("%s,", order);
                    }
                    Query.Sorter sorter = sorters.next();
                    String sortKey = sorter.getKey();
                    if (sorter instanceof Query.KeySorter) {
                        order = String.format("%s objects.key %s", order, sorter.getType());
                    } else if (includedSorts.containsKey(sortKey)) {
                        order = String.format("%s %s.value %s", order, includedSorts.get(sortKey), sorter.getType());
                    } else {
                        // join in the sorting field it wasn't used in a search
                        filters = String.format("%s LEFT JOIN indexes AS i%d ON objects.bucket = i%d.bucket AND objects.key = i%d.key AND i%d.name=?", filters, i, i, i, i);
                        names.add(sorter.getKey());
                        order = String.format("%s i%d.value %s", order, i, sorter.getType());                        
                        i++;
                    }
                }
            } else {
                order = "";
            }
            String statement = String.format("%s %s %s %s", selection, filters, where, order);
            names.addAll(replacements);
            String[] args = names.toArray(new String[names.size()]);
            Log.d(TAG, String.format("Query: %s | %s", statement, names));
            return buildCursor(schema, database.rawQuery(statement, args, cancelSignal));
        }
        
        private void index(T object, List<Index> indexValues){
            // delete all current idexes
            deleteIndexes(object);
            Log.d(TAG, String.format("Index %d values for %s", indexValues.size(), object.getSimperiumKey()));
            Iterator<Index> indexes = indexValues.iterator();
            while(indexes.hasNext()){
                Index index = indexes.next();
                ContentValues values = new ContentValues(4);
                values.put("bucket", bucketName);
                values.put("key", object.getSimperiumKey());
                values.put("name", index.getName());
                String key = "value";
                // figure out the type of value
                Object value = index.getValue();
                if (value instanceof Byte) {
                    values.put(key, (Byte) value);
                } else if(value instanceof Integer){
                    values.put(key, (Integer) value);
                } else if(value instanceof Float){
                    values.put(key, (Float) value);
                } else if(value instanceof Short){
                    values.put(key, (Short) value);
                } else if(value instanceof String){
                    values.put(key, (String) value);
                } else if(value instanceof Double){
                    values.put(key, (Double) value);
                } else if(value instanceof Long){
                    values.put(key, (Long) value);
                } else if(value instanceof Boolean){
                    values.put(key, (Boolean) value);
                } else {
                    values.put(key, value.toString());
                }
                database.insertOrThrow(INDEXES_TABLE, null, values);
            }
        }

        private void deleteIndexes(T object){
            database.delete(INDEXES_TABLE, "bucket=? AND key=?", new String[]{bucketName, object.getSimperiumKey()});
        }

        private void deleteAllIndexes(){
            database.delete(INDEXES_TABLE, "bucket=?", new String[]{bucketName});
        }
    }

    private class ObjectCursor<T extends Syncable> extends CursorWrapper implements Bucket.ObjectCursor<T> {
        
        private BucketSchema<T> schema;

        ObjectCursor(BucketSchema<T> schema, Cursor cursor){
            super(cursor);
            this.schema = schema;
        }

        public String getSimperiumKey(){
            return getString(getColumnIndexOrThrow("object_key"));
        }

        public T getObject(){
            String key = getSimperiumKey();
            try {
                JSONObject data = new JSONObject(getString(getColumnIndex("object_data")));
                return schema.build(key, Channel.convertJSON(data));
            } catch (org.json.JSONException e) {
                return schema.build(key, new HashMap<String,Object>());
            }
        }
    }

    private <T extends Syncable> Bucket.ObjectCursor<T> buildCursor(BucketSchema<T> schema, Cursor cursor){
        return new ObjectCursor<T>(schema, cursor);
    }
    
    private void configure(){
        // create and validate the tables we'll be using for the datastore
        configureObjects();
        configureIndexes();
    }
        
    private void configureIndexes(){
        Cursor tableInfo = tableInfo(INDEXES_TABLE);
        if (tableInfo.getCount() == 0) {
            // create the table
            database.execSQL(String.format("CREATE TABLE %s (bucket, key, name, value)", INDEXES_TABLE));
        }
    }

    private void configureObjects(){
        Cursor tableInfo = tableInfo(OBJECTS_TABLE);
        if (tableInfo.getCount() == 0) {
            database.execSQL(String.format("CREATE TABLE %s (bucket, key, data)", OBJECTS_TABLE));
        }
    }

    private Cursor tableInfo(String tableName){
        return database.rawQuery(String.format("PRAGMA table_info(%s)", tableName), null);
    }

}