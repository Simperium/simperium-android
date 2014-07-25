package com.simperium.android;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.simperium.BuildConfig;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.BucketSchema;
import com.simperium.client.BucketSchema.Index;
import com.simperium.client.FullTextIndex;
import com.simperium.client.Query;
import com.simperium.client.Syncable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PersistentStore implements StorageProvider {
    public static final String TAG="Simperium.Store";
    public static final String OBJECTS_TABLE="objects";
    public static final String INDEXES_TABLE="indexes";
    public static final String REINDEX_QUEUE_TABLE="reindex_queue";

    private SQLiteDatabase mDatabase;

    public PersistentStore(SQLiteDatabase database) {
        mDatabase = database;
        configure();
    }

    public Cursor queryObject(String bucketName, String key) {
        return mDatabase.query(OBJECTS_TABLE, new String[]{"objects.rowid AS _id", "objects.bucket", "objects.key as `object_key`", "objects.data as `object_data`"}, "bucket=? AND key=?", new String[]{bucketName, key}, null, null, null, "1");
    }

    @Override
    public <T extends Syncable> BucketStore<T> createStore(String bucketName, BucketSchema<T> schema) {
        return new DataStore<T>(bucketName, schema);
    }

    protected class DataStore<T extends Syncable> implements StorageProvider.BucketStore<T> {

        final protected BucketSchema<T> mSchema;
        final protected String mBucketName;
        private Reindexer mReindexer;

        DataStore(String bucketName, BucketSchema<T> schema) {
            mSchema = schema;
            mBucketName = bucketName;
        }

        public void reindex(final Bucket<T> bucket) {
            mReindexer = new Reindexer(bucket);

            mReindexer.start();
        }

        @Override
        public void prepare(Bucket<T> bucket) {
            setupFullText();

            // Clear reindex table to stop any other indexing operations
            mDatabase.delete(REINDEX_QUEUE_TABLE, "bucket=?", new String[]{ mBucketName });

            reindex(bucket);
        }

        /**
         * Add/Update the given object
         */
        @Override
        public void save(T object, List<Index> indexes) {
            String key = object.getSimperiumKey();
            mReindexer.skip(key);
            ContentValues values = new ContentValues();
            values.put("bucket", mBucketName);
            values.put("key", key);
            values.put("data", object.getDiffableValue().toString());
            Cursor cursor = queryObject(mBucketName, key);
            if (cursor.getCount() == 0) {
                mDatabase.insert(OBJECTS_TABLE, null, values);
            } else {
                mDatabase.update(OBJECTS_TABLE, values, "bucket=? AND key=?", new String[]{mBucketName, key});
            }
            index(object, indexes);
            if (BuildConfig.DEBUG) Log.d(TAG, "Saved indexes for " + object);
        }

        /**
         * Remove the given object from the storage
         */
        @Override
        public void delete(T object) {
            String key = object.getSimperiumKey();
            mReindexer.skip(key);
            mDatabase.delete(OBJECTS_TABLE, "bucket=? AND key=?", new String[]{mBucketName, key});
            deleteIndexes(object);
        }

        /**
         * Delete all objects from storage
         */
        @Override
        public void reset() {
            if (mReindexer != null) mReindexer.stop();
            mDatabase.delete(OBJECTS_TABLE, "bucket=?", new String[]{mBucketName});
            if (mSchema.hasFullTextIndex())
                mDatabase.delete(getFullTextTableName(), null, null);
            deleteAllIndexes();
        }

        /**
         * Get an object with the given key
         */
        @Override
        public T get(String key) throws BucketObjectMissingException {
            ObjectCursor<T> cursor = buildCursor(mSchema, queryObject(mBucketName, key));
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
        public Bucket.ObjectCursor<T> all() {
            return buildCursor(mSchema, mDatabase.query(false, OBJECTS_TABLE,
                    new String[]{"objects.rowid AS _id", "objects.bucket", "objects.key as `object_key`", "objects.data as `object_data`"},
                    "bucket=?", new String[]{mBucketName}, null, null, null, null));
        }

        /**
         * Count for the given query
         */
        public int count(Query<T> query) {
            QueryBuilder builder = new QueryBuilder(this, query);
            Cursor cursor = builder.count(mDatabase);
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
        public Bucket.ObjectCursor<T> search(Query<T> query) {
            QueryBuilder builder = new QueryBuilder(this, query);
            Cursor cursor = builder.query(mDatabase);
            return buildCursor(mSchema, cursor);
        }
        
        protected void index(T object, List<Index> indexValues)
        throws SQLException {
            // delete all current idexes
            mDatabase.beginTransaction();
            deleteIndexes(object);

            for(Index index : indexValues) {
                ContentValues values = new ContentValues(4);
                values.put("bucket", mBucketName);
                values.put("key", object.getSimperiumKey());
                values.put("name", index.getName());
                String key = "value";
                // figure out the type of value
                Object value = index.getValue();
                if (value instanceof Byte) {
                    values.put(key, (Byte) value);
                } else if(value instanceof Integer) {
                    values.put(key, (Integer) value);
                } else if(value instanceof Float) {
                    values.put(key, (Float) value);
                } else if(value instanceof Short) {
                    values.put(key, (Short) value);
                } else if(value instanceof String) {
                    values.put(key, (String) value);
                } else if(value instanceof Double) {
                    values.put(key, (Double) value);
                } else if(value instanceof Long) {
                    values.put(key, (Long) value);
                } else if(value instanceof Boolean) {
                    values.put(key, (Boolean) value);
                } else if(value != null) {
                    values.put(key, value.toString());
                }
                try {
                    mDatabase.insertOrThrow(INDEXES_TABLE, key, values);
                } catch (SQLException e) {
                    mDatabase.endTransaction();
                    throw e;
                }
            }

            // If we have a fulltext index, let's add a record
            if (mSchema.hasFullTextIndex()) {
                Map<String,String> fullTextValues = mSchema.getFullTextIndex().index(object);
                ContentValues fullTextIndexes = new ContentValues(fullTextValues.size());

                for(Map.Entry<String,String> entry : fullTextValues.entrySet()) {
                    fullTextIndexes.put(entry.getKey(), entry.getValue());
                }

                String ftTableName = getFullTextTableName();
                mDatabase.delete(ftTableName, "key=?", new String[]{ object.getSimperiumKey() });
                if (fullTextIndexes.size() > 0) {
                    fullTextIndexes.put("key", object.getSimperiumKey());
                    try {
                        mDatabase.insertOrThrow(ftTableName, null, fullTextIndexes);
                    } catch (SQLException e) {
                        mDatabase.endTransaction();
                        throw e;
                    }
                }

            }
            mDatabase.setTransactionSuccessful();
            mDatabase.endTransaction();
        }

        private void deleteIndexes(T object) {
            mDatabase.delete(INDEXES_TABLE, "bucket=? AND key=?", new String[]{mBucketName, object.getSimperiumKey()});
            if (mSchema.hasFullTextIndex()) {
                String tableName = getFullTextTableName();
                mDatabase.delete(tableName, "key=?", new String[]{ object.getSimperiumKey() });
            }
        }

        private void deleteAllIndexes() {
            mDatabase.delete(INDEXES_TABLE, "bucket=?", new String[]{mBucketName});
        }

        private void setupFullText() {
            if (mSchema.hasFullTextIndex()) {
                boolean rebuild = false;

                FullTextIndex index = mSchema.getFullTextIndex();
                String[] keys = index.getKeys();
                List<String> columns = new ArrayList<String>(keys.length + 1);
                columns.add("key");
                Collections.addAll(columns, keys);

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
                    mDatabase.execSQL(String.format(Locale.US, "DROP TABLE IF EXISTS `%s`", tableName));
                    StringBuilder fields = new StringBuilder();
                    for (String key : keys) {
                        fields.append("`");
                        fields.append(key);
                        fields.append("`, ");
                    }
                    fields.append("`key`");
                    String query = String.format(Locale.US, "CREATE VIRTUAL TABLE `%s` USING fts3(%s)", tableName, fields.toString());
                    mDatabase.execSQL(query);
                }
            }
        }

        protected String getFullTextTableName() {
            return String.format(Locale.US, "%s_ft", mBucketName);
        }

        private class Reindexer implements Runnable {

            final private Thread mReindexThread;
            final private Bucket<T> mBucket;

            Reindexer(Bucket<T> bucket) {
                mBucket = bucket;
                mReindexThread = new Thread(this, String.format("%s-reindexer", bucket.getName()));
                mReindexThread.setPriority(Thread.MIN_PRIORITY);
            }

            public void start() {
                String query = String.format(Locale.US, "INSERT INTO reindex_queue SELECT bucket, key FROM objects WHERE bucket = '%s'", mBucket.getName());
                mDatabase.execSQL(query);
                mReindexThread.start();
            }

            public void stop() {
                mReindexThread.interrupt();
            }

            public void skip(String key) {
                mDatabase.delete(REINDEX_QUEUE_TABLE, "bucket=? AND key=?", new String[]{ mBucket.getName(), key});
            }

            @Override
            public void run() {
                String bucketName = mBucket.getName();
                String[] fields = new String[]{ "key" };
                String[] args = new String[]{ bucketName };
                String conditions = "bucket=?";
                String deleteConditions = "bucket=? AND key=?";
                String limit = "1";
                try {

                    Thread.sleep(1000);

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Starting reindex process for: " + mBucket.getName());
                    }

                    while(true) {
                        if (Thread.interrupted()) throw new InterruptedException();

                        Cursor next = mDatabase.query(REINDEX_QUEUE_TABLE, fields, conditions, args, null, null, null, limit);
                        if (next.getCount() == 0) {
                            next.close();
                            break;
                        }
                        next.moveToFirst();
                        String key = next.getString(0);
                        try {
                            T object = mBucket.get(key);
                            index(object, mSchema.indexesFor(object));
                        } catch (BucketObjectMissingException e) {
                            // object is gone
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Reindexer could not find object `" + key + "` in bucket " + mBucket.getName());
                            }
                        }
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Reindexed `" + bucketName + "." + key + "`");
                        }
                        mDatabase.delete(REINDEX_QUEUE_TABLE, deleteConditions, new String[]{bucketName, key});
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Indexing interrupted " + bucketName, e);
                    mDatabase.delete(REINDEX_QUEUE_TABLE, conditions, args);
                } catch (SQLException e) {
                    Log.e(TAG, "SQL Error " + bucketName, e);
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Done indexing " + bucketName);
                mBucket.notifyOnNetworkChangeListeners(Bucket.ChangeType.INDEX);
            }

        }

    }

    class ObjectCursor<T extends Syncable> extends CursorWrapper implements Bucket.ObjectCursor<T> {
        
        private BucketSchema<T> mSchema;

        int mObjectKeyColumn;
        int mObjectDataColumn;

        ObjectCursor(BucketSchema<T> schema, Cursor cursor) {
            super(cursor);
            mSchema = schema;
            mObjectKeyColumn = getColumnIndexOrThrow("object_key");
            mObjectDataColumn = getColumnIndexOrThrow("object_data");
        }

        @Override
        public String getSimperiumKey() {
            return super.getString(mObjectKeyColumn);
        }

        @Override
        public T getObject() {
            String key = getSimperiumKey();
            try {
                JSONObject data = new JSONObject(super.getString(mObjectDataColumn));
                return mSchema.buildWithDefaults(key, data);
            } catch (org.json.JSONException e) {
                return mSchema.buildWithDefaults(key, new JSONObject());
            }
        }

    }

    private <T extends Syncable> ObjectCursor<T> buildCursor(BucketSchema<T> schema, Cursor cursor) {
        return new ObjectCursor<T>(schema, cursor);
    }
    
    private void configure() {
        // create and validate the tables we'll be using for the datastore
        configureObjects();
        configureIndexes();
    }
        
    private void configureIndexes() {
        Cursor tableInfo = tableInfo(INDEXES_TABLE);
        if (tableInfo.getCount() == 0) {
            // create the table
            mDatabase.execSQL(String.format(Locale.US, "CREATE TABLE %s (bucket, key, name, value)", INDEXES_TABLE));
        }
        tableInfo.close();
        mDatabase.execSQL(String.format(Locale.US, "CREATE INDEX IF NOT EXISTS index_name ON %s(bucket, key, name)", INDEXES_TABLE));
        mDatabase.execSQL(String.format(Locale.US, "CREATE INDEX IF NOT EXISTS index_value ON %s(bucket, key, value)", INDEXES_TABLE));
        mDatabase.execSQL(String.format(Locale.US, "CREATE INDEX IF NOT EXISTS index_key ON %s(bucket, key)", INDEXES_TABLE));
    }

    private void configureObjects() {
        Cursor tableInfo = tableInfo(OBJECTS_TABLE);
        if (tableInfo.getCount() == 0) {
            mDatabase.execSQL(String.format(Locale.US, "CREATE TABLE %s (bucket, key, data)", OBJECTS_TABLE));
        }
        tableInfo.close();
        mDatabase.execSQL(String.format(Locale.US, "CREATE UNIQUE INDEX IF NOT EXISTS bucket_key ON %s (bucket, key)", OBJECTS_TABLE));
        mDatabase.execSQL(String.format(Locale.US, "CREATE INDEX IF NOT EXISTS object_key ON %s (key)", OBJECTS_TABLE));

        mDatabase.execSQL("CREATE TABLE IF NOT EXISTS reindex_queue (bucket, key)");
        mDatabase.execSQL("CREATE INDEX IF NOT EXISTS reindex_bucket ON reindex_queue(bucket)");
        mDatabase.execSQL("CREATE INDEX IF NOT EXISTS reindex_key ON reindex_queue(key)");
    }

    protected Cursor tableInfo(String tableName) {
        return mDatabase.rawQuery(String.format(Locale.US, "PRAGMA table_info(`%s`)", tableName), null);
    }

    protected static class QueryBuilder {

        private Query mQuery;
        private DataStore mDataStore;
        protected StringBuilder mSelection;
        protected String mStatement;
        protected String[] mArgs;

        QueryBuilder(DataStore store, Query query) {
            mDataStore = store;
            mQuery = query;
            compileQuery();
        }

        protected Cursor query(SQLiteDatabase database) {
            String query = mSelection.append(mStatement).toString();
            if (mQuery.hasLimit()) {
                query += String.format(Locale.US, " LIMIT %d", mQuery.getLimit());
                if (mQuery.hasOffset())
                    query += String.format(Locale.US, ", %d", mQuery.getOffset());
            }
            return database.rawQuery(query, mArgs);
        }

        protected Cursor count(SQLiteDatabase database) {
            mSelection = new StringBuilder("SELECT count(objects.rowid) as `total` ");
            return database.rawQuery(mSelection.append(mStatement).toString(), mArgs);
        }

        private void compileQuery() {
            // turn comparators into where statements, each comparator joins
            List<Query.Condition> conditions = mQuery.getConditions();
            List<Query.Sorter> sorters = mQuery.getSorters();
            List<Query.Field> fields = mQuery.getFields();
            String bucketName = mDataStore.mBucketName;
            String ftName = mDataStore.getFullTextTableName();

            mSelection = new StringBuilder("SELECT DISTINCT objects.rowid AS `_id`, objects.bucket || objects.key AS `key`, objects.key as `object_key`, objects.data as `object_data` ");
            StringBuilder filters = new StringBuilder();
            StringBuilder where = new StringBuilder("WHERE objects.bucket = ?");

            List<String> replacements = new ArrayList<String>(1);
            replacements.add(bucketName);
            List<String> names = new ArrayList<String>(1);
            // table include index for alias
            int i = 0;

            List<String> sortKeys = new ArrayList<String>();
            Map<String,String> includedKeys = new HashMap<String,String>();
            Boolean includedFullText = false;

            for(Query.Sorter sorter : sorters) {
                sortKeys.add(sorter.getKey());
            }

            String fullTextFilter = null;
            for(Query.Condition condition : conditions) {
                String key = condition.getKey();

                if (condition.getComparisonType() == Query.ComparisonType.MATCH) {
                    // include the full text index table if not already included
                    if(!includedFullText)
                        fullTextFilter = String.format(Locale.US, " JOIN `%s` ON objects.key = `%s`.`key` ", ftName, ftName);

                    includedFullText = true;
                    // add the condition and argument to the where statement
                    String field = key == null ? ftName : String.format(Locale.US, "`%s`.`%s`", ftName, condition.getKey());
                    where.append(String.format(Locale.US, " AND ( %s %s ? )", field, condition.getComparisonType()));
                    replacements.add(condition.getSubject().toString());
                    continue;
                }

                // store which keys have been joined in and which alias
                includedKeys.put(key, String.format(Locale.US, "i%d", i));
                names.add(condition.getKey());
                filters.append(String.format(Locale.US, " LEFT JOIN indexes AS i%d ON objects.bucket = i%d.bucket AND objects.key = i%d.key AND i%d.name=?", i, i, i, i));
                Object subject = condition.getSubject();

                // short circuit for null subjects
                if (subject == null) {

                    switch(condition.getComparisonType()) {

                        case EQUAL_TO :
                        case LIKE :
                            where.append(String.format(Locale.US, " AND ( i%d.value IS NULL ) ", i));
                            break;

                        case NOT_EQUAL_TO :
                        case NOT_LIKE :
                            where.append(String.format(Locale.US, " AND ( i%d.value NOT NULL ) ", i));
                            break;

                        default :
                            // noop
                            break;

                    }

                    i++;

                    continue;
                }

                String null_condition = condition.includesNull() ? String.format(Locale.US, " i%d.value IS NULL OR", i) : String.format(Locale.US, " i%d.value IS NOT NULL AND", i);
                where.append(String.format(Locale.US, " AND ( %s i%d.value %s ", null_condition, i, condition.getComparisonType()));
                if (subject instanceof Float) {
                    where.append(String.format(Locale.US, " %f)", (Float)subject));
                } else if (subject instanceof Integer) {
                    where.append(String.format(Locale.US, " %d)", (Integer)subject));
                } else if (subject instanceof Boolean) {
                    where.append(String.format(Locale.US, " %d)", ((Boolean)subject ? 1 : 0)));
                } else if (subject != null) {
                    where.append(" ?)");
                    replacements.add(subject.toString());
                }

                i++;
            }

            if(includedFullText) filters.insert(0, fullTextFilter);

            for(Query.Field field : fields) {

                if (field instanceof Query.FullTextSnippet) {
                    Query.FullTextSnippet snippet = (Query.FullTextSnippet) field;
                    int ftColumnIndex = mDataStore.mSchema.getFullTextIndex().getColumnIndex(snippet.getColumnName());
                    mSelection.append(String.format(Locale.US, ", snippet(`%s`, '<match>', '</match>', '\u2026', %d) AS %s", ftName, ftColumnIndex, field.getName()));
                    continue;
                } else if (field instanceof Query.FullTextOffsets) {
                    mSelection.append(String.format(", offsets(`%s`) AS %s", ftName, field.getName()));
                    continue;
                }

                String fieldName = field.getName();
                if (!includedKeys.containsKey(fieldName)) {
                    includedKeys.put(fieldName, String.format(Locale.US, "i%d", i));
                    names.add(fieldName);
                    filters.append(String.format(Locale.US, " LEFT JOIN indexes AS i%d ON objects.bucket = i%d.bucket AND objects.key = i%d.key AND i%d.name=?", i, i, i, i));
                    i++;
                }
                mSelection.append(String.format(Locale.US, ", %s.value AS `%s`", includedKeys.get(fieldName), fieldName));
                
            }

            StringBuilder order = new StringBuilder("ORDER BY");
            int orderLength = order.length();
            if (sorters.size() > 0) {
                for(Query.Sorter sorter : sorters) {
                    if (order.length() != orderLength) {
                        order.append(", ");
                    }
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
            mStatement = String.format(Locale.US, " FROM `objects` %s %s %s", filters.toString(), where.toString(), order.toString());
            names.addAll(replacements);
            mArgs = names.toArray(new String[names.size()]);
        }

    }


}
