package com.simperium.testapp;

import android.test.ActivityInstrumentationTestCase2;
import static android.test.MoreAsserts.*;
import static com.simperium.testapp.TestHelpers.*;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;

import com.simperium.android.PersistentStore;
import com.simperium.android.LoginActivity;

import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema;
import com.simperium.client.Query;
import com.simperium.client.ObjectCacheProvider.ObjectCache;
import com.simperium.client.GhostStorageProvider;
import com.simperium.client.Syncable;
import com.simperium.client.User;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.storage.StorageProvider.BucketStore;

import com.simperium.util.Uuid;

import com.simperium.testapp.models.Note;

import com.simperium.testapp.mock.MockCache;
import com.simperium.testapp.mock.MockChannel;
import com.simperium.testapp.mock.MockSyncService;
import com.simperium.testapp.mock.MockGhostStore;

import java.util.List;
import java.util.ArrayList;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;

public class PersistentStoreTest extends ActivityInstrumentationTestCase2<LoginActivity> {
    public static final String TAG = "SimperiumTest.PersistentStore";
    public static final String MASTER_TABLE = "sqlite_master";
    public static final String BUCKET_NAME="bucket";
    
    private LoginActivity mActivity;
    
    private PersistentStore mStore;
    private BucketStore<Note> mNoteStore;
    private SQLiteDatabase mDatabase;
    private String mDatabaseName = "simperium-test-data";
    private String[] mTableNames = new String[]{"indexes", "objects", "value_caches"};
    private Bucket<Note> mBucket;
    private User mUser;
    private BucketSchema mSchema;
    private ObjectCache<Note> mCache;
    private GhostStorageProvider mGhostStore;

    public PersistentStoreTest() {
        super("com.simperium.client.test", LoginActivity.class);
    }

    @Override
    protected void setUp() throws Exception {

        super.setUp();

        setActivityInitialTouchMode(false);
        mUser = makeUser();
        mActivity = getActivity();
        mDatabase = mActivity.openOrCreateDatabase(mDatabaseName, 0, null);
        mGhostStore = new MockGhostStore();
        mCache = new MockCache<Note>();
        mStore = new PersistentStore(mDatabase);
        mSchema = new Note.Schema();
        mNoteStore = mStore.createStore(BUCKET_NAME, mSchema);
        mBucket = new Bucket<Note>(MockSyncService.service(), BUCKET_NAME, mSchema, mUser, mNoteStore, mGhostStore, mCache);
        Bucket.Channel<Note> channel = new MockChannel<Note>(mBucket);
        mBucket.setChannel(channel);
        mNoteStore.prepare(mBucket);
    }

    @Override
    protected void tearDown() throws Exception {
        mActivity.deleteDatabase(mDatabaseName);
        super.tearDown();
    }

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
        
        Bucket.ObjectCursor<Note> cursor = mNoteStore.all();
        assertEquals(2, cursor.getCount());
    }
  
    public void testResetData()
    throws Exception {
        String bucketName = BUCKET_NAME;
  
        Note note = mBucket.newObject("note-1");
        note.setTitle("Booh yah!");
        note.save();
  
        Bucket.ObjectCursor<Note> cursor = mNoteStore.all();
        assertEquals(1, cursor.getCount());
  
        mNoteStore.reset();
  
        cursor = mNoteStore.all();
        assertEquals(0, cursor.getCount());
  
    }
  
    public void testIndexObject()
    throws Exception {
        String bucketName = BUCKET_NAME;
        List<String> list = new ArrayList<String>();
        list.add("uno");
        list.add("dos");
        list.add("tres");

        Note note = mBucket.newObject("hola");
        note.put("col1", "Hello");
        note.put("col2", 237);
        note.put("col3", 245.12);
        note.put("col4", list);

        note.save();

        Cursor cursor = mDatabase.query(PersistentStore.INDEXES_TABLE, null, null, null, null, null, "name", null);
        assertEquals(8, cursor.getCount());
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
        BucketStore<Note> store = mStore.createStore(bucketName, schema);
        mBucket = new Bucket<Note>(MockSyncService.service(), BUCKET_NAME, mSchema, mUser, store, mGhostStore, mCache);

        store.prepare(mBucket);

        Bucket.ObjectCursor<Note> cursor;

        Query<Note> query = new Query<Note>();
        query.where("special", Query.ComparisonType.EQUAL_TO, true);
        cursor = store.search(query);
        assertEquals(20, cursor.getCount());

        query = new Query<Note>();
        query.where("title", Query.ComparisonType.LIKE, "Note 7%");
        cursor = store.search(query);
        assertEquals(111, cursor.getCount());
        
        query = new Query<Note>();
        query.where("title", Query.ComparisonType.NOT_LIKE, "Note 7%");
        cursor = store.search(query);
        assertEquals(889, cursor.getCount());
        
        query = new Query<Note>();
        query.where("special", Query.ComparisonType.NOT_EQUAL_TO, true);
        cursor = store.search(query);
        assertEquals(980, cursor.getCount());
        
        query = new Query<Note>();
        query.where("special", Query.ComparisonType.NOT_EQUAL_TO, false);
        query.where("title", Query.ComparisonType.LIKE, "Note 1%");
        cursor = store.search(query);
        assertEquals(110, cursor.getCount());

        query = new Query<Note>();
        query.where("spanish", Query.ComparisonType.EQUAL_TO, "cuatro");
        cursor = store.search(query);
        assertEquals(10, cursor.getCount());
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
        BucketStore<Note> store = mStore.createStore(bucketName, schema);

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
        BucketStore<Note> store = mStore.createStore(bucketName, schema);
        Bucket<Note> bucket = new Bucket<Note>(MockSyncService.service(), bucketName, mSchema, mUser, store, mGhostStore, mCache);
        store.prepare(bucket);
        bucket.setChannel(new MockChannel<Note>(bucket));
        
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
        Bucket.ObjectCursor<Note> cursor = store.search(query);
        assertEquals(3, cursor.getCount());
        cursor.moveToFirst();
        Note note = cursor.getObject();
        assertEquals(1, note.get("position"));
        

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

        query = new Query<Note>();
        query.order("position");
        query.where("position", Query.ComparisonType.GREATER_THAN, 1);
        cursor = store.search(query);
        assertEquals(2, cursor.getCount());
        cursor.moveToFirst();
        note = cursor.getObject();
        assertEquals(2, note.get("position"));
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
        Bucket.ObjectCursor<Note> cursor = query.execute();
        cursor.moveToFirst();
        assertEquals(5, cursor.getColumnCount());
        assertEquals("Lol", cursor.getString(4));
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
        note.setContent("Hello world. Hola mundo. The world is your oyster.");
        note.addTag("world");
        note.save();

        Query query = mBucket.query().where(new Query.FullTextMatch("world"));
        query.include(new Query.FullTextSnippet("match"));

        Cursor cursor = query.execute();
        cursor.moveToFirst();

        assertEquals("Hello <b>world</b>. Hola mundo. The <b>world</b> is your oyster.", cursor.getString(cursor.getColumnIndexOrThrow("match")));

    }

    public static void assertTableExists(SQLiteDatabase database, String tableName){
        Cursor cursor = database.query(MASTER_TABLE, new String[]{"name"}, "type=? AND name=?", new String[]{"table", tableName}, "name", null, null, null);
        assertEquals(String.format("Table %s does not exist in %s", tableName, database), 1, cursor.getCount());
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
