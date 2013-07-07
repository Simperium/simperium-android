package com.simperium.tests;

import android.test.ActivityInstrumentationTestCase2;
import static android.test.MoreAsserts.*;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;

import com.simperium.storage.PersistentStore;

import com.simperium.client.Bucket;
import com.simperium.client.Query;
import com.simperium.client.Syncable;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.storage.StorageProvider.BucketStore;

import com.simperium.util.Uuid;


import com.simperium.tests.models.Note;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PersistentStoreTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public static final String TAG = MainActivity.TAG;
    public static final String MASTER_TABLE = "sqlite_master";
    
    private MainActivity mActivity;
    
    private PersistentStore mStore;
    private SQLiteDatabase mDatabase;
    private String mDatabaseName = "simperium-test-data";
    private String[] mTableNames = new String[]{"indexes", "objects", "value_caches"};
    private Bucket<Note> mBucket;
    public PersistentStoreTest() {
        super("com.simperium.tests", MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {

        super.setUp();

        setActivityInitialTouchMode(false);
        mActivity = getActivity();
        mDatabase = mActivity.openOrCreateDatabase(mDatabaseName, 0, null);
        mStore = new PersistentStore(mDatabase);

    }

    @Override
    protected void tearDown() throws Exception {
        mActivity.deleteDatabase(mDatabaseName);
        super.tearDown();
    }

    public void testDatabaseTables(){
        assertTableExists(mDatabase, "objects");
        assertTableExists(mDatabase, "indexes");
    }
  
    public void testSavingObject(){
        String bucketName = "bucket";
        String key = "test";
        Note.Schema schema = new Note.Schema();
        BucketStore<Note> store = mStore.createStore(bucketName, schema);
        Note note = new Note(key, new HashMap<String,Object>());
  
        store.save(note);
        note.setTitle("Hola Mundo!");
        store.save(note);
  
        Cursor cursor = mStore.queryObject(bucketName, key);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals(bucketName, cursor.getString(0));
        assertEquals(key, cursor.getString(1));
        assertEquals("{\"title\":\"Hola Mundo!\"}", cursor.getString(2));
    }
  
    public void testDeletingObject(){
        String bucketName = "bucket";
        String key = "test";
        Note.Schema schema = new Note.Schema();
        BucketStore<Note> store = mStore.createStore(bucketName, schema);
        Note note = new Note(key, new HashMap<String,Object>());
        
        note.setTitle("LOL!");
        store.save(note);
        store.delete(note);
  
        Cursor cursor = mStore.queryObject(bucketName, key);
        assertEquals(0, cursor.getCount());
    }
  
    public void testGettingObject() throws BucketObjectMissingException {
        String bucketName = "bucket";
        String key = "test";
        Note.Schema schema = new Note.Schema();
        BucketStore<Note> store = mStore.createStore(bucketName, schema);
        Note note = new Note(key, new HashMap<String,Object>());
  
        note.setTitle("Booh yah!");
        store.save(note);
        
        Note restored = store.get(key);
  
        assertEquals(note.getSimperiumKey(), restored.getSimperiumKey());
        assertEquals(note.getTitle(), restored.getTitle());
    }
  
    public void testObjectCursor(){
        String bucketName = "bucket";
        Note.Schema schema = new Note.Schema();
        BucketStore<Note> store = mStore.createStore(bucketName, schema);
  
        Note note = new Note("note-1", new HashMap<String,Object>());
        note.setTitle("Booh yah!");
  
        Note note2 = new Note("note-2", new HashMap<String,Object>());
        note2.setTitle("Other note");
        
        store.save(note);
        store.save(note2);
        
        Bucket.ObjectCursor<Note> cursor = store.all(null);
        assertEquals(2, cursor.getCount());
    }
  
    public void testResetData(){
        String bucketName = "bucket";
        Note.Schema schema = new Note.Schema();
        BucketStore<Note> store = mStore.createStore(bucketName, schema);
  
        Note note = new Note("note-1", new HashMap<String,Object>());
        note.setTitle("Booh yah!");
        store.save(note);
  
        Bucket.ObjectCursor<Note> cursor = store.all(null);
        assertEquals(1, cursor.getCount());
  
        store.reset();
  
        cursor = store.all(null);
        assertEquals(0, cursor.getCount());
  
    }
  
    public void testIndexObject(){
        String bucketName = "bucket";
        Note.Schema schema = new Note.Schema();
        BucketStore<Note> store = mStore.createStore(bucketName, schema);
        List<String> list = new ArrayList<String>();
        list.add("uno");
        list.add("dos");
        list.add("tres");

        Note note = new Note("hola", new HashMap<String,Object>());
        note.put("col1", "Hello");
        note.put("col2", 237);
        note.put("col3", 245.12);
        note.put("col4", list);
  
        store.save(note);
  
        Cursor cursor = mDatabase.query(PersistentStore.INDEXES_TABLE, null, null, null, null, null, "name", null);
        assertEquals(6, cursor.getCount());
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

    public void testObjectSearching(){
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
        // build a bunch of notes
        // for (int i=0; i<1000; i++) {
        //     Note note = new Note(Uuid.uuid(), new HashMap<String,Object>());
        //     note.put("title", String.format("Note %d", i));
        //     if(i % 50 == 0){
        //         note.put("special", true);
        //     }
        //     if (i == 1) {
        //         note.put("special", false);
        //     }
        //     if (i % 10 == 0) {
        //         List<String> list = new ArrayList<String>();
        //         list.add("uno");
        //         list.add("dos");
        //         list.add("tres");
        //         if (i % 100 == 0) {
        //             list.add("cuatro");
        //         }
        //         note.put("spanish", list);
        //     }
        //     store.save(note);
        // }
        // 
        // if(true) throw new RuntimeException("copy the db");

        Bucket.ObjectCursor<Note> cursor;

        Query<Note> query = new Query<Note>();
        query.where("special", Query.ComparisonType.EQUAL_TO, true);
        cursor = store.search(query, null);
        assertEquals(20, cursor.getCount());

        query = new Query<Note>();
        query.where("title", Query.ComparisonType.LIKE, "Note 7%");
        cursor = store.search(query, null);
        assertEquals(111, cursor.getCount());
        
        query = new Query<Note>();
        query.where("title", Query.ComparisonType.NOT_LIKE, "Note 7%");
        cursor = store.search(query, null);
        assertEquals(889, cursor.getCount());
        
        query = new Query<Note>();
        query.where("special", Query.ComparisonType.NOT_EQUAL_TO, true);
        cursor = store.search(query, null);
        assertEquals(980, cursor.getCount());
        
        query = new Query<Note>();
        query.where("special", Query.ComparisonType.NOT_EQUAL_TO, false);
        query.where("title", Query.ComparisonType.LIKE, "Note 1%");
        cursor = store.search(query, null);
        assertEquals(110, cursor.getCount());

        query = new Query<Note>();
        query.where("spanish", Query.ComparisonType.EQUAL_TO, "cuatro");
        cursor = store.search(query, null);
        assertEquals(10, cursor.getCount());
    }

    public void testObjectSorting(){
        String bucketName = "notes";
        Note.Schema schema = new Note.Schema();
        BucketStore<Note> store = mStore.createStore(bucketName, schema);
        Note first = new Note("first", new HashMap<String,Object>());
        Note second = new Note("second", new HashMap<String,Object>());
        Note third = new Note("third", new HashMap<String,Object>());

        first.put("position", 1);
        second.put("position", 2);
        third.put("position", 3);

        third.put("title", "zzz");
        second.put("title", "aaa");
        first.put("title", "aaa");

        second.put("backwards", 1);
        first.put("backwards", 2);

        store.save(third);
        store.save(second);
        store.save(first);

        Query<Note> query = new Query<Note>();
        query.order("position");
        Bucket.ObjectCursor<Note> cursor = store.search(query, null);
        assertEquals(3, cursor.getCount());
        cursor.moveToFirst();
        Note note = cursor.getObject();
        assertEquals(1, note.get("position"));
        

        query = new Query<Note>();
        query.order("title", Query.SortType.DESCENDING);
        query.order("backwards");
        cursor = store.search(query, null);
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
        cursor = store.search(query, null);
        assertEquals(2, cursor.getCount());
        cursor.moveToFirst();
        note = cursor.getObject();
        assertEquals(2, note.get("position"));
    }

    public static void assertTableExists(SQLiteDatabase database, String tableName){
        Cursor cursor = database.query(MASTER_TABLE, new String[]{"name"}, "type=? AND name=?", new String[]{"table", tableName}, "name", null, null, null);
        assertEquals(String.format("Table %s does not exist in %s", tableName, database), 1, cursor.getCount());
    }

}
