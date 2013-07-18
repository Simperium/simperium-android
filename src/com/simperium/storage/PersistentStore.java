package com.simperium.storage;

import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.BucketSchema.Index;
import com.simperium.client.Syncable;
import com.simperium.client.Channel;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.Query;

import android.database.sqlite.SQLiteDatabase;
import android.database.SQLException;
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
import java.util.Collections;

import org.json.JSONObject;
import org.json.JSONException;

import android.util.Log;

public class PersistentStore implements StorageProvider {
    public static final String TAG="Simperium.Store";
    public static final String OBJECTS_TABLE="objects";
    public static final String INDEXES_TABLE="indexes";

    private SQLiteDatabase database;
    private Thread reindexThread;
    private List<String> skipIndexing = Collections.synchronizedList(new ArrayList<String>());

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
            reindex();
        }

        public void reindex(){
            final CancellationSignal signal = new CancellationSignal();
            if (reindexThread != null && reindexThread.isAlive()) {
                return;
            }
            reindexThread = new Thread(new Runnable(){
                @Override
                public void run(){
                    Bucket.ObjectCursor<T> cursor = all(null);
                    while(cursor.moveToNext()){
                        try {
                            T object = cursor.getObject();
                            String key = cursor.getSimperiumKey();
                            if (skipIndexing.contains(key)) {
                                Log.d(TAG, String.format("Skipped reindexing %s", key));
                                continue;
                            }
                            index(object, schema.indexesFor(object));
                            object.notifySaved();
                        } catch (SQLException e) {
                            Thread.currentThread().interrupt();
                            Log.d(TAG, "Reindexing canceled due to exception", e);
                        }
                        if (Thread.interrupted()) {
                            signal.cancel();
                            cursor.close();
                            skipIndexing.clear();
                            return;
                        }
                    }
                    cursor.close();
                    skipIndexing.clear();
                }
            });
            reindexThread.start();
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
            skipIndexing.add(key);
            index(object, indexes);
        }

        /**
         * Remove the given object from the storage
         */
        @Override
        public void delete(T object){
            String key = object.getSimperiumKey();
            skipIndexing.add(key);
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
                cursor.close();
                throw(new BucketObjectMissingException());
            } else {
                cursor.moveToFirst();                
                T object = cursor.getObject();
                cursor.close();
                return object;
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
         */
        @Override
        public Bucket.ObjectCursor<T> search(Query<T> query, CancellationSignal cancelSignal){
            CursorBuilder builder = new CursorBuilder(bucketName, query);
            Cursor cursor = builder.query(database, cancelSignal);
            return buildCursor(schema, cursor);
        }
        
        protected void index(T object, List<Index> indexValues){
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

    private class CursorBuilder {

        private Query query;
        private String statement;
        private String[] args;
        private String bucketName;

        CursorBuilder(String bucketName, Query query){
            this.bucketName = bucketName;
            this.query = query;
            compileQuery();
        }

        public Cursor query(SQLiteDatabase database, CancellationSignal cancelSignal){
            return database.rawQuery(statement.toString(), args, cancelSignal);
        }

        private void compileQuery(){
            // turn comparators into where statements, each comparator joins
            Iterator<Query.Comparator> conditions = query.getConditions().iterator();
            Iterator<Query.Sorter> sorters = query.getSorters().iterator();
            Iterator<String> keys = query.getKeys().iterator();

            StringBuilder selection = new StringBuilder("SELECT DISTINCT objects.rowid AS `_id`, objects.bucket || objects.key AS `key`, objects.key as `object_key`, objects.data as `object_data`");
            StringBuilder filters = new StringBuilder();
            StringBuilder where = new StringBuilder("WHERE objects.bucket = ?");

            List<String> replacements = new ArrayList<String>(1);
            replacements.add(bucketName);
            List<String> names = new ArrayList<String>(1);
            // table include index for alias
            int i = 0;

            List<String> sortKeys = new ArrayList<String>();
            Map<String,String> includedKeys = new HashMap<String,String>();
            while(sorters.hasNext()){
                sortKeys.add(sorters.next().getKey());
            }

            while(conditions.hasNext()){
                Query.Comparator condition = conditions.next();
                String key = condition.getKey();
                // store which keys have been joined in and which alias
                includedKeys.put(key, String.format("i%d", i));
                names.add(condition.getKey());
                filters.append(String.format(" LEFT JOIN indexes AS i%d ON objects.bucket = i%d.bucket AND objects.key = i%d.key AND i%d.name=?", i, i, i, i));
                Object subject = condition.getSubject();
                String null_condition = condition.includesNull() ? String.format(" i%d.value IS NULL OR", i) : String.format(" i%d.value IS NOT NULL AND", i);
                where.append(String.format(" AND ( %s i%d.value %s ", null_condition, i, condition.getComparisonType()));
                if (subject instanceof Float) {
                    where.append(String.format(" %f)", (Float)subject));
                } else if (subject instanceof Integer){
                    where.append(String.format(" %d)", (Integer)subject));
                } else if (subject instanceof Boolean){
                    where.append(String.format(" %d)", ((Boolean)subject ? 1 : 0)));
                } else {
                    where.append(" ?)");
                    replacements.add(subject.toString());
                }
                i++;
            }

            while(keys.hasNext()){
                String key = keys.next();
                if (!includedKeys.containsKey(key)) {
                    includedKeys.put(key, String.format("i%d", i));
                    names.add(key);
                    filters.append(String.format(" LEFT JOIN indexes AS i%d ON objects.bucket = i%d.bucket AND objects.key = i%d.key AND i%d.name=?", i, i, i, i));
                    i++;
                }
                selection.append(String.format(", %s.value AS `%s`", includedKeys.get(key), key));
                
            }

            StringBuilder order = new StringBuilder("ORDER BY");
            int orderLength = order.length();
            if (query.getSorters().size() > 0){
                sorters = query.getSorters().iterator();
                while(sorters.hasNext()){
                    if (order.length() != orderLength) {
                        order.append(", ");
                    }
                    Query.Sorter sorter = sorters.next();
                    String sortKey = sorter.getKey();
                    if (sorter instanceof Query.KeySorter) {
                        order.append(String.format(" objects.key %s", sorter.getType()));
                    } else if (includedKeys.containsKey(sortKey)) {
                        order.append(String.format(" %s.value %s", includedKeys.get(sortKey), sorter.getType()));
                    } else {
                        // join in the sorting field it wasn't used in a search
                        filters.append(String.format(" LEFT JOIN indexes AS i%d ON objects.bucket = i%d.bucket AND objects.key = i%d.key AND i%d.name=?", i, i, i, i));
                        names.add(sorter.getKey());
                        order.append(String.format(" i%d.value %s", i, sorter.getType()));
                        i++;
                    }
                }
            } else {
                order.delete(0, order.length());
            }
            statement = String.format("%s FROM `objects` %s %s %s", selection.toString(), filters.toString(), where.toString(), order.toString());
            names.addAll(replacements);
            args = names.toArray(new String[names.size()]);
            Log.d(TAG, String.format("Query: %s | %s", statement, names));
        }

    }

}