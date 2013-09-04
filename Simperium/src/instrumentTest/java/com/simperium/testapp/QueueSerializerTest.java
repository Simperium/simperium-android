package com.simperium.testapp;

import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;

import static android.test.MoreAsserts.*;
import static com.simperium.testapp.TestHelpers.*;

import com.simperium.android.LoginActivity;
import com.simperium.android.QueueSerializer;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;

public class QueueSerializerTest extends ActivityInstrumentationTestCase2<LoginActivity> {

    protected QueueSerializer mSerializer;
    protected SQLiteDatabase mDatabase;

    public QueueSerializerTest() {
        super(LoginActivity.class);
    }

    protected void setUp() throws Exception {
        mDatabase = getActivity().openOrCreateDatabase("queue-test", 0, null);
        mSerializer = new QueueSerializer(mDatabase);
    }

    public void testDatabaseSetup() throws Exception {
        assertTableExists(mDatabase, QueueSerializer.TABLE_NAME);
    }

    public static void assertTableExists(SQLiteDatabase database, String tableName) {
        Cursor cursor = database.query("sqlite_master", new String[]{"name"}, "type=? AND name=?", new String[]{"table", tableName}, "name", null, null, null);
        assertEquals(String.format("Table %s does not exist in %s", tableName, database), 1, cursor.getCount());
    }

}