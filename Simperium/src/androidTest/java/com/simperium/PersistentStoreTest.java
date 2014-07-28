package com.simperium.android;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.simperium.android.PersistentStore;
import com.simperium.android.Bucket.ObjectCursor;
import com.simperium.client.Query;
import com.simperium.test.model.Note;
import com.simperium.test.MockChannel;
import com.simperium.test.MockExecutor;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PersistentStoreTest extends PersistentStoreBaseTest {

    public void testDatabaseTables()
    throws Exception {
        assertTableExists(mDatabase, "objects");
        assertTableExists(mDatabase, "indexes");
        assertTableExists(mDatabase, "bucket_ft");
    }
  
    public void testSavingObject()
    throws Exception {
        String bucketName = BUCKET_NAME;
        String key = "test";
        Note note = mBucket.newObject(key);// new Note(key, new HashMap<String,Object>());
  
        note.save();
        note.setTitle("Hola Mundo!");
        note.save();
  
        Cursor cursor = mStore.queryObject(bucketName, key);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals(bucketName, cursor.getString(1));
        assertEquals(key, cursor.getString(2));
        assertEquals("{\"tags\":[],\"deleted\":false,\"title\":\"Hola Mundo!\"}", cursor.getString(3));
        cursor.close();
    }
  
    public void testDeletingObject()
    throws Exception {
        String bucketName = BUCKET_NAME;
        String key = "test";
        Note note = mBucket.newObject(key);
        
        note.setTitle("LOL!");
        note.save();
        note.delete();
  
        Cursor cursor = mStore.queryObject(bucketName, key);
        assertEquals(0, cursor.getCount());
        cursor.close();
    }
  
    public void testGettingObject()
    throws Exception {
        String bucketName = BUCKET_NAME;
        String key = "test";
        
        Note note = mBucket.newObject(key);
  
        note.setTitle("Booh yah!");
        note.save();
        
        Note restored = mBucket.get(key);
  
        assertEquals(note.getSimperiumKey(), restored.getSimperiumKey());
        assertEquals(note.getTitle(), restored.getTitle());
    }
  
    public void testObjectCursor()
    throws Exception {
        String bucketName = BUCKET_NAME;
  
        Note note = mBucket.newObject("note-1");
        note.setTitle("Booh yah!");
  
        Note note2 = mBucket.newObject("note-2");
        note2.setTitle("Other note");
        
        note.save();
        note2.save();
        
        ObjectCursor<Note> cursor = mNoteStore.all();
        assertEquals(2, cursor.getCount());
        cursor.close();
    }
  
    public void testResetData()
    throws Exception {
        String bucketName = BUCKET_NAME;
  
        Note note = mBucket.newObject("note-1");
        note.setTitle("Booh yah!");
        note.save();
  
        ObjectCursor<Note> cursor = mNoteStore.all();
        assertEquals(1, cursor.getCount());
        cursor.close();
  
        mNoteStore.reset();
  
        cursor = mNoteStore.all();
        assertEquals(0, cursor.getCount());
        cursor.close();
    }
  
    public void testIndexObject()
    throws Exception {
        String bucketName = BUCKET_NAME;
        JSONArray list = new JSONArray();
        list.put("uno");
        list.put("dos");
        list.put("tres");

        Note note = mBucket.newObject("hola");
        note.put("col1", "Hello");
        note.put("col2", 237);
        note.put("col3", 245.12);
        note.put("col4", list);

        note.save();

        Cursor cursor = mDatabase.query(PersistentStore.INDEXES_TABLE, null, null, null, null, null, "name", null);
        assertEquals(9, cursor.getCount());
        cursor.moveToFirst();
        assertEquals(bucketName, cursor.getString(0));
        assertEquals(note.getSimperiumKey(), cursor.getString(1));
        assertEquals("col1", cursor.getString(2));
        assertEquals("Hello", cursor.getString(3));
  
        cursor.moveToNext();
        assertEquals("col2", cursor.getString(2));
        assertEquals("237", cursor.getString(3));
        assertEquals(237, cursor.getInt(3));

        cursor.moveToNext();
        cursor.moveToNext();
        assertEquals("col4", cursor.getString(2));
        assertEquals("uno", cursor.getString(3));

        cursor.moveToNext();
        assertEquals("col4", cursor.getString(2));
        assertEquals("dos", cursor.getString(3));

        cursor.close();
    }

    public void testObjectSearching()
    throws Exception {
        // clear out the database
        mActivity.deleteDatabase(mDatabaseName);
        
        String bucketName = "notes";
        Note.Schema schema = new Note.Schema();
        // Copy the data from the asset database using the same database name
        // it will get cleaned up by the tearDown method
        DatabaseHelper helper = new DatabaseHelper(mDatabaseName);
        mDatabaseName = helper.getDatabaseName();
        helper.createDatabase();
        mStore = new PersistentStore(helper.getWritableDatabase());
        StorageProvider.BucketStore<Note> store = mStore.createStore(bucketName, schema);
        mBucket = new Bucket<Note>(MockExecutor.immediate(), BUCKET_NAME, mSchema, mUser, store, mGhostStore);

        store.prepare(mBucket);

        ObjectCursor<Note> cursor;

        Query<Note> query = new Query<Note>();
        query.where("special", Query.ComparisonType.EQUAL_TO, true);
        cursor = store.search(query);
        assertEquals(20, cursor.getCount());
        cursor.close();

        query = new Query<Note>();
        query.where("title", Query.ComparisonType.LIKE, "Note 7%");
        cursor = store.search(query);
        assertEquals(111, cursor.getCount());
        cursor.close();

        query = new Query<Note>();
        query.where("title", Query.ComparisonType.NOT_LIKE, "Note 7%");
        cursor = store.search(query);
        assertEquals(889, cursor.getCount());
        cursor.close();

        query = new Query<Note>();
        query.where("special", Query.ComparisonType.NOT_EQUAL_TO, true);
        cursor = store.search(query);
        assertEquals(980, cursor.getCount());
        cursor.close();

        query = new Query<Note>();
        query.where("special", Query.ComparisonType.NOT_EQUAL_TO, false);
        query.where("title", Query.ComparisonType.LIKE, "Note 1%");
        cursor = store.search(query);
        assertEquals(110, cursor.getCount());
        cursor.close();

        query = new Query<Note>();
        query.where("spanish", Query.ComparisonType.EQUAL_TO, "cuatro");
        cursor = store.search(query);
        assertEquals(10, cursor.getCount());
        cursor.close();
    }

    public void testQueryWithNullSubject()
    throws Exception {

        Query<Note> query = new Query<Note>(mBucket);

        // with issue #90 this would throw android.database.sqlite.SQLiteException
        ObjectCursor<Note> cursor = mBucket.searchObjects(query.where("title", Query.ComparisonType.LIKE, null));

        cursor.close();
    }

    /**
     * Search for notes with a null value in "null_column" index
     */
    public void testSearchForNull() {

        Note note = mBucket.newObject();
        note.setTitle("Special will be null");
        note.save();

        int count = mBucket.query().where("null_column", Query.ComparisonType.EQUAL_TO, null).count();

        assertEquals(1, count);

    }

    public void testCounts()
    throws Exception {
        // clear out the database
        mActivity.deleteDatabase(mDatabaseName);
        
        String bucketName = "notes";
        Note.Schema schema = new Note.Schema();
        // Copy the data from the asset database using the same database name
        // it will get cleaned up by the tearDown method
        DatabaseHelper helper = new DatabaseHelper(mDatabaseName);
        mDatabaseName = helper.getDatabaseName();
        helper.createDatabase();
        mStore = new PersistentStore(helper.getWritableDatabase());
        StorageProvider.BucketStore<Note> store = mStore.createStore(bucketName, schema);

        int count;

        count = store.count(new Query<Note>());
        assertEquals(1000, count);

        Query<Note> query = new Query<Note>();
        query.where("title", Query.ComparisonType.LIKE, "Note 7%");
        count = store.count(query);
        assertEquals(111, count);

    }

    public void testObjectSorting()
    throws Exception {
        String bucketName = "notes";
        Note.Schema schema = new Note.Schema();
        StorageProvider.BucketStore<Note> store = mStore.createStore(bucketName, schema);
        Bucket<Note> bucket = new Bucket<Note>(MockExecutor.immediate(), bucketName, mSchema, mUser, store, mGhostStore);
        store.prepare(bucket);
        bucket.setChannel(new MockChannel(bucket));
        
        Note first = bucket.newObject("first");
        Note second = bucket.newObject("second");
        Note third = bucket.newObject("third");

        first.put("position", 1);
        second.put("position", 2);
        third.put("position", 3);

        third.put("title", "zzz");
        second.put("title", "aaa");
        first.put("title", "aaa");

        second.put("backwards", 1);
        first.put("backwards", 2);

        third.save();
        second.save();
        first.save();

        Query<Note> query = new Query<Note>();
        query.order("position");
        ObjectCursor<Note> cursor = store.search(query);
        assertEquals(3, cursor.getCount());
        cursor.moveToFirst();
        Note note = cursor.getObject();
        assertEquals(1, note.get("position"));
        cursor.close();

        query = new Query<Note>();
        query.order("title", Query.SortType.DESCENDING);
        query.order("backwards");
        cursor = store.search(query);
        assertEquals(3, cursor.getCount());
        cursor.moveToFirst();
        note = cursor.getObject();
        assertEquals(3, note.get("position"));
        cursor.moveToNext();
        note = cursor.getObject();
        assertEquals(2, note.get("position"));
        cursor.close();

        query = new Query<Note>();
        query.order("position");
        query.where("position", Query.ComparisonType.GREATER_THAN, 1);
        cursor = store.search(query);
        assertEquals(2, cursor.getCount());
        cursor.moveToFirst();
        note = cursor.getObject();
        assertEquals(2, note.get("position"));
        cursor.close();

    }

    /**
     * Tests pulling indexed values from the cursor for performance.
     */
    public void testSelectIndexValues()
    throws Exception {
        Note note = mBucket.newObject("thing");
        note.setContent("Lol");
        note.save();

        Query<Note> query = mBucket.query().include("preview");
        ObjectCursor<Note> cursor = mBucket.searchObjects(query);
        cursor.moveToFirst();
        assertEquals(5, cursor.getColumnCount());
        assertEquals("Lol", cursor.getString(4));
        cursor.close();
    }

    public void testFullTextSearching()
    throws Exception {
        Note note = mBucket.newObject("ftsearch");
        note.setContent("Hello world. Town hall is starting in four minutes.");
        note.addTags("one", "two", "three");
        note.save();

        note = mBucket.newObject("ftsearch2");
        note.setContent("It was the best of times, it was the worst of times. two.");
        note.addTags("red", "green", "blue", "yellow");
        note.save();

        note = mBucket.newObject("ftsearch3");
        note.setContent("lorem ipsum dolor whatever");
        note.addTags("literature");

        assertEquals(1, mBucket.query().where(new Query.FullTextMatch("town hall")).count());

        assertEquals(2, mBucket.query().where(new Query.FullTextMatch("two")).count());

        assertEquals(1, mBucket.query().where(new Query.FullTextMatch("tags:two")).count());

    }

    public void testFullTextSnippet()
    throws Exception {
        Note note = mBucket.newObject("ftsnippet");
        note.setContent("Hello world. Hola mundo. The world is your oyster. Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
        note.addTag("world");
        note.save();

        Query query = mBucket.query().where(new Query.FullTextMatch("world"));
        query.include(new Query.FullTextSnippet("match"));

        Cursor cursor = mBucket.searchObjects(query);
        cursor.moveToFirst();

        assertEquals("Hello <match>world</match>. Hola mundo. The <match>world</match> is your oyster. Lorem ipsum dolor sit amet, consectetur\u2026", cursor.getString(cursor.getColumnIndexOrThrow("match")));
        cursor.close();
    }

    public static void assertTableExists(SQLiteDatabase database, String tableName){
        Cursor cursor = database.query(MASTER_TABLE, new String[]{"name"}, "type=? AND name=?", new String[]{"table", tableName}, "name", null, null, null);
        assertEquals(String.format("Table %s does not exist in %s", tableName, database), 1, cursor.getCount());
        cursor.close();
    }

    private class DatabaseHelper extends SQLiteOpenHelper {

        private static final String ASSET_NAME = "query-test-data";

        public DatabaseHelper(String db_name){
            super(mActivity, db_name, null, 1);
        }

        public void createDatabase() {
            try {
                copyDatabase();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onCreate(SQLiteDatabase db){
            // do nothing
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int from, int to){
            // do nothing
        }

        private void copyDatabase() throws IOException {
            InputStream input = mActivity.getAssets().open(ASSET_NAME);
            File dbPath = mActivity.getDatabasePath(getDatabaseName());
            OutputStream output = new FileOutputStream(dbPath);

            byte[] buffer = new byte[1024];
            int length;
            while((length = input.read(buffer)) > 0){
                output.write(buffer, 0, length);
            }
            output.flush();
            output.close();
            input.close();
        }

    }

}
