package com.simperium.android;

import com.simperium.storage.StorageProvider;
import com.simperium.storage.StorageProvider.BucketStore;
import com.simperium.client.Bucket;
import com.simperium.client.Bucket.ChangeType;
import com.simperium.client.BucketSchema;
import com.simperium.client.BucketSchema.Index;
import com.simperium.client.Syncable;
import com.simperium.client.Channel;
import com.simperium.client.FullTextIndex;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.Query;
import com.simperium.util.Logger;

import android.database.sqlite.SQLiteDatabase;
import android.database.SQLException;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.content.ContentValues;
import android.os.CancellationSignal;
import android.os.Handler;

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;
import java.util.Locale;

import org.json.JSONObject;
import org.json.JSONException;

public class PersistentStore implements StorageProvider {
    public static final String TAG="Simperium.Store";
    public static final String OBJECTS_TABLE="objects";
    public static final String INDEXES_TABLE="indexes";
    public static final String REINDEX_QUEUE_TABLE="reindex_queue";

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
        private Reindexer mReindexer;

        DataStore(String bucketName, BucketSchema<T> schema){
            this.schema = schema;
            this.bucketName = bucketName;
        }

        public void reindex(final Bucket<T> bucket){
            mReindexer = new Reindexer(bucket);
            mReindexer.start();
        }

        @Override
        public void prepare(Bucket<T> bucket){
            setupFullText();
            reindex(bucket);
        }

        /**
         * Add/Update the given object
         */
        @Override
        public void save(T object, List<Index> indexes){
            String key = object.getSimperiumKey();
            mReindexer.skip(key);
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
            mReindexer.skip(key);
            database.delete(OBJECTS_TABLE, "bucket=? AND key=?", new String[]{bucketName, key});
            deleteIndexes(object);
        }

        /**
         * Delete all objects from storage
         */
        @Override
        public void reset(){
            if (mReindexer != null) mReindexer.stop();
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
        public Bucket.ObjectCursor<T> all(){
            return buildCursor(schema, database.query(false, OBJECTS_TABLE,
                    new String[]{"objects.rowid AS _id", "objects.bucket", "objects.key as `object_key`", "objects.data as `object_data`"},
                    "bucket=?", new String[]{bucketName}, null, null, null, null));
        }

        /**
         * Count for the given query
         */
        public int count(Query<T> query){
            QueryBuilder builder = new QueryBuilder(bucketName, query);
            Cursor cursor = builder.count(database);
            cursor.moveToFirst();
            int count = cursor.getInt(0);
            cursor.close();
            return count;
        }

        /**
         * Search the datastore using the given Query
         * 
         */
        @Override
        public Bucket.ObjectCursor<T> search(Query<T> query){
            QueryBuilder builder = new QueryBuilder(bucketName, query);
            Cursor cursor = builder.query(database);
            return buildCursor(schema, cursor);
        }
        
        protected void index(T object, List<Index> indexValues){
            // delete all current idexes
            deleteIndexes(object);
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

            // If we have a fulltext index, let's add a record
            if (schema.hasFullTextIndex()) {
                ContentValues fullTextValues = schema.getFullTextIndex().index(object);
                String ftTableName = getFullTextTableName();
                database.delete(ftTableName, "key=?", new String[]{ object.getSimperiumKey() });
                if (fullTextValues.size() > 0) {
                    fullTextValues.put("key", object.getSimperiumKey());
                    database.insertOrThrow(ftTableName, null, fullTextValues);
                }
            }
        }

        private void deleteIndexes(T object){
            database.delete(INDEXES_TABLE, "bucket=? AND key=?", new String[]{bucketName, object.getSimperiumKey()});
            if (schema.hasFullTextIndex()) {
                String tableName = getFullTextTableName();
                database.delete(tableName, "key=?", new String[]{ object.getSimperiumKey() });
            }
        }

        private void deleteAllIndexes(){
            database.delete(INDEXES_TABLE, "bucket=?", new String[]{bucketName});
        }

        private void setupFullText() {
            if (schema.hasFullTextIndex()) {
                boolean rebuild = false;

                FullTextIndex index = schema.getFullTextIndex();
                String[] keys = index.getKeys();
                List<String> columns = new ArrayList<String>(keys.length + 1);
                columns.add("key");
                for (String key :keys) {
                    columns.add(key);
                }

                String tableName = getFullTextTableName();
                Cursor tableInfo = tableInfo(tableName);
                int nameColumn = tableInfo.getColumnIndex("name");

                if (tableInfo.getCount() == 0) rebuild = true;
                while (tableInfo.moveToNext()) {
                    columns.remove(tableInfo.getString(nameColumn));
                }
                if (columns.size() > 0) rebuild = true;
                tableInfo.close();

                if (rebuild) {
                    database.execSQL(String.format(Locale.US, "DROP TABLE IF EXISTS `%s`", tableName));
                    StringBuilder fields = new StringBuilder("`key`");
                    for (String key : keys) {
                        fields.append(", ");
                        fields.append("`");
                        fields.append(key);
                        fields.append("`");
                    }
                    String query = String.format(Locale.US, "CREATE VIRTUAL TABLE `%s` USING fts3(%s)", tableName, fields.toString());
                    Logger.log("Simperium.FullText", String.format("Rebuilding full text index: %s", query));
                    database.execSQL(query);
                }
            }
        }

        private String getFullTextTableName(){
            return String.format(Locale.US, "%s_ft", bucketName);
        }

        private class Reindexer implements Runnable {

            final private Thread mReindexThread;
            final private Bucket<T> mBucket;

            Reindexer(Bucket<T> bucket){
                mBucket = bucket;
                mReindexThread = new Thread(this, String.format("%s-reindexer", bucket.getName()));
                mReindexThread.setPriority(Thread.MIN_PRIORITY);
            }

            public void start(){
                String query = String.format(Locale.US, "INSERT INTO reindex_queue SELECT bucket, key FROM objects WHERE bucket = '%s'", mBucket.getName());
                database.execSQL(query);
                mReindexThread.start();
            }

            public void stop(){
                mReindexThread.interrupt();
            }

            public void skip(String key){
                database.delete(REINDEX_QUEUE_TABLE, "bucket=? AND key=?", new String[]{ mBucket.getName(), key});
            }

            @Override
            public void run(){
                String bucketName = mBucket.getName();
                String[] fields = new String[]{ "key" };
                String[] args = new String[]{ bucketName };
                String conditions = "bucket=?";
                String deleteConditions = "bucket=? AND key=?";
                String limit = "1";
                try {
                    while(true){
                        if (Thread.interrupted()) throw new InterruptedException();

                        Cursor next = database.query(REINDEX_QUEUE_TABLE, fields, conditions, args, null, null, null, limit);
                        if (next.getCount() == 0){
                            next.close();
                            break;
                        }
                        next.moveToFirst();
                        String key = next.getString(0);
                        try {
                            T object = mBucket.get(key);
                            index(object, schema.indexesFor(object));
                        } catch (BucketObjectMissingException e) {
                            // object is gone
                        }
                        database.delete(REINDEX_QUEUE_TABLE, deleteConditions, new String[]{bucketName, key});
                        Thread.currentThread().sleep(1);
                    }
                } catch (InterruptedException e) {
                    Logger.log(TAG, String.format("Indexing interrupted %s", bucketName), e);
                    database.delete(REINDEX_QUEUE_TABLE, conditions, args);
                } catch (SQLException e) {
                    Logger.log(TAG, String.format("SQL Error %s", bucketName), e);
                }
                Logger.log(TAG, String.format("Done indexing %s", bucketName));
            }

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
                return schema.buildWithDefaults(key, Channel.convertJSON(data));
            } catch (org.json.JSONException e) {
                return schema.buildWithDefaults(key, new HashMap<String,Object>());
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
            database.execSQL(String.format(Locale.US, "CREATE TABLE %s (bucket, key, name, value)", INDEXES_TABLE));
        }
        tableInfo.close();
        database.execSQL(String.format(Locale.US, "CREATE INDEX IF NOT EXISTS index_name ON %s(bucket, key, name)", INDEXES_TABLE));
        database.execSQL(String.format(Locale.US, "CREATE INDEX IF NOT EXISTS index_value ON %s(bucket, key, value)", INDEXES_TABLE));
        database.execSQL(String.format(Locale.US, "CREATE INDEX IF NOT EXISTS index_key ON %s(bucket, key)", INDEXES_TABLE));
    }

    private void configureObjects(){
        Cursor tableInfo = tableInfo(OBJECTS_TABLE);
        if (tableInfo.getCount() == 0) {
            database.execSQL(String.format(Locale.US, "CREATE TABLE %s (bucket, key, data)", OBJECTS_TABLE));
        }
        tableInfo.close();
        database.execSQL(String.format(Locale.US, "CREATE UNIQUE INDEX IF NOT EXISTS bucket_key ON %s (bucket, key)", OBJECTS_TABLE));

        database.execSQL("CREATE TABLE IF NOT EXISTS reindex_queue (bucket, key)");
        database.execSQL("CREATE INDEX IF NOT EXISTS reindex_bucket ON reindex_queue(bucket)");
        database.execSQL("CREATE INDEX IF NOT EXISTS reindex_key ON reindex_queue(key)");
    }

    protected Cursor tableInfo(String tableName){
        return database.rawQuery(String.format(Locale.US, "PRAGMA table_info(`%s`)", tableName), null);
    }

    private class QueryBuilder {

        private Query query;
        private StringBuilder selection;
        private String statement;
        private String[] args;
        private String bucketName;

        QueryBuilder(String bucketName, Query query){
            this.bucketName = bucketName;
            this.query = query;
            compileQuery();
        }

        protected Cursor query(SQLiteDatabase database){
            String query = selection.append(statement).toString();
            // android.util.Log.d(TAG, String.format("Query: %s Args: %s", query, args));
            return database.rawQuery(query, args);
        }

        protected Cursor count(SQLiteDatabase database){
            selection = new StringBuilder("SELECT count(objects.rowid) as `total` ");
            return database.rawQuery(selection.append(statement).toString(), args);
        }

        private void compileQuery(){
            // turn comparators into where statements, each comparator joins
            Iterator<Query.Condition> conditions = query.getConditions().iterator();
            Iterator<Query.Sorter> sorters = query.getSorters().iterator();
            Iterator<String> keys = query.getKeys().iterator();

            selection = new StringBuilder("SELECT objects.rowid AS `_id`, objects.bucket || objects.key AS `key`, objects.key as `object_key`, objects.data as `object_data` ");
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
                Query.Condition condition = conditions.next();
                String key = condition.getKey();
                // store which keys have been joined in and which alias
                includedKeys.put(key, String.format(Locale.US, "i%d", i));
                names.add(condition.getKey());
                filters.append(String.format(Locale.US, " LEFT JOIN indexes AS i%d ON objects.bucket = i%d.bucket AND objects.key = i%d.key AND i%d.name=?", i, i, i, i));
                Object subject = condition.getSubject();
                String null_condition = condition.includesNull() ? String.format(Locale.US, " i%d.value IS NULL OR", i) : String.format(Locale.US, " i%d.value IS NOT NULL AND", i);
                where.append(String.format(Locale.US, " AND ( %s i%d.value %s ", null_condition, i, condition.getComparisonType()));
                if (subject instanceof Float) {
                    where.append(String.format(Locale.US, " %f)", (Float)subject));
                } else if (subject instanceof Integer){
                    where.append(String.format(Locale.US, " %d)", (Integer)subject));
                } else if (subject instanceof Boolean){
                    where.append(String.format(Locale.US, " %d)", ((Boolean)subject ? 1 : 0)));
                } else {
                    where.append(" ?)");
                    replacements.add(subject.toString());
                }
                i++;
            }

            while(keys.hasNext()){
                String key = keys.next();
                if (!includedKeys.containsKey(key)) {
                    includedKeys.put(key, String.format(Locale.US, "i%d", i));
                    names.add(key);
                    filters.append(String.format(Locale.US, " LEFT JOIN indexes AS i%d ON objects.bucket = i%d.bucket AND objects.key = i%d.key AND i%d.name=?", i, i, i, i));
                    i++;
                }
                selection.append(String.format(Locale.US, ", %s.value AS `%s`", includedKeys.get(key), key));
                
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
                        order.append(String.format(Locale.US, " objects.key %s", sorter.getType()));
                    } else if (includedKeys.containsKey(sortKey)) {
                        order.append(String.format(Locale.US, " %s.value %s", includedKeys.get(sortKey), sorter.getType()));
                    } else {
                        // join in the sorting field it wasn't used in a search
                        filters.append(String.format(Locale.US, " LEFT JOIN indexes AS i%d ON objects.bucket = i%d.bucket AND objects.key = i%d.key AND i%d.name=?", i, i, i, i));
                        names.add(sorter.getKey());
                        order.append(String.format(Locale.US, " i%d.value %s", i, sorter.getType()));
                        i++;
                    }
                }
            } else {
                order.delete(0, order.length());
            }
            statement = String.format(Locale.US, "FROM `objects` %s %s %s", filters.toString(), where.toString(), order.toString());
            names.addAll(replacements);
            args = names.toArray(new String[names.size()]);
        }

    }


}
