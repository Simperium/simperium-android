package com.simperium.tests;

import android.test.ActivityInstrumentationTestCase2;
import static android.test.MoreAsserts.*;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;

import com.simperium.storage.PersistentStore;

import com.simperium.client.Bucket;
import com.simperium.client.Syncable;
import com.simperium.client.BucketObjectMissingException;

import com.simperium.storage.StorageProvider.BucketStore;
import com.simperium.storage.StorageProvider.BucketCursor;

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
        
        BucketCursor<Note> cursor = store.all();
        assertEquals(2, cursor.getCount());
    }

    public void testResetData(){
        String bucketName = "bucket";
        Note.Schema schema = new Note.Schema();
        BucketStore<Note> store = mStore.createStore(bucketName, schema);

        Note note = new Note("note-1", new HashMap<String,Object>());
        note.setTitle("Booh yah!");
        store.save(note);

        BucketCursor<Note> cursor = store.all();
        assertEquals(1, cursor.getCount());

        store.reset();

        cursor = store.all();
        assertEquals(0, cursor.getCount());
        
    }
    public static void assertTableExists(SQLiteDatabase database, String tableName){
        Cursor cursor = database.query(MASTER_TABLE, new String[]{"name"}, "type=? AND name=?", new String[]{"table", tableName}, "name", null, null, null);
        assertEquals(String.format("Table %s does not exist in %s", tableName, database), 1, cursor.getCount());
    }

}
