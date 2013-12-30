package com.simperium;

import com.simperium.client.Change;
import com.simperium.client.Bucket;
import com.simperium.client.Syncable;

import com.simperium.models.Note;

import com.simperium.test.MockBucket;

import junit.framework.TestCase;

import org.json.JSONObject;

public class ChangeTest extends TestCase {

    private Bucket<Note> mBucket;
    private Note mNote;

    protected void setUp()
    throws Exception {

        mBucket = MockBucket.buildBucket(new Note.Schema());

    }

    public void testModifyPayload()
    throws Exception {

        Note note = mBucket.newObject();
        note.setTitle("Hello world");

        Change change = new Change(Change.OPERATION_MODIFY, note);

        assertTrue(change.isModifyOperation());
        assertValidChangeObject(note, change);

        JSONObject diff = change.toJSONObject().getJSONObject("v");

        String expected = "{\"tags\":{\"v\":[],\"o\":\"+\"},\"deleted\":{\"v\":false,\"o\":\"+\"},\"title\":{\"v\":\"Hello world\",\"o\":\"+\"}}";
        assertEquals(expected, diff.toString());

    }

    public void testDeletePayload()
    throws Exception {

        Note note = mBucket.newObject();
        note.setTitle("Hello world");
        note.save();

        Change change = new Change(Change.OPERATION_REMOVE, note);

        assertTrue(change.isRemoveOperation());
        assertValidChangeObject(note, change);

    }

    public static void assertValidChangeObject(Syncable object, Change change)
    throws Exception {

        JSONObject changeJSON = change.toJSONObject();

        assertNotNull("Change missing ccid", changeJSON.optString("ccid"));
        assertNotNull("Change missing operation key `o`", changeJSON.optString("o"));
        assertEquals("Change id is incorrect", object.getSimperiumKey(), changeJSON.getString("id"));

        if (change.isRemoveOperation()) {
            assertFalse("Remove operation has a diff value", changeJSON.has("v"));
        } else {
            assertNotNull("Modify operation has no patch", changeJSON.optJSONObject("v"));
        }
    }

}