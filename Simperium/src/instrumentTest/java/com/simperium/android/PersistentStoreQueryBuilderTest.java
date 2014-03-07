package com.simperium.android;

import android.test.MoreAsserts;

import com.simperium.android.PersistentStore.QueryBuilder;
import com.simperium.android.PersistentStore.DataStore;
import com.simperium.client.Query;

import com.simperium.models.Note;

public class PersistentStoreQueryBuilderTest extends PersistentStoreBaseTest {

    public void testEqualToComparisonQuery()
    throws Exception {

        Query query = new Query();
        query.where("title", Query.ComparisonType.EQUAL_TO, "lol");
        QueryBuilder builder = buildQuery(query);

        String[] args = new String[] {
            "title", // index name
            "bucket", // bucket name
            "lol" // value to compare to
        };
        String condition = " FROM `objects`  LEFT JOIN indexes AS i0 ON objects.bucket = i0.bucket AND objects.key = i0.key AND i0.name=? WHERE objects.bucket = ? AND (  i0.value IS NOT NULL AND i0.value =  ?) ";

        MoreAsserts.assertEquals(args, builder.args);
        assertEquals(condition.toString(), builder.statement.toString());


    }

    public void testEqualToNullComparisonQuery() {

        Query query = new Query();
        query.where("title", Query.ComparisonType.EQUAL_TO, null);
        QueryBuilder builder = buildQuery(query);

        String[] args = new String[] {
            "title", // index name
            "bucket", // bucket name
        };
        String condition = " FROM `objects`  LEFT JOIN indexes AS i0 ON objects.bucket = i0.bucket AND objects.key = i0.key AND i0.name=? WHERE objects.bucket = ? AND (  i0.value IS NULL ) ";

        MoreAsserts.assertEquals(args, builder.args);
        assertEquals(condition.toString(), builder.statement.toString());

    }

    protected QueryBuilder buildQuery(Query query) {
        return new QueryBuilder((DataStore<Note>) mNoteStore, query);
    }

}