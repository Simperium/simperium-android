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
        mNote = mBucket.newObject();
        mNote.setTitle("Hello world");

    }

    public void testModifyPayload()
    throws Exception {

        Change change = new Change(Change.OPERATION_MODIFY, mNote);

        assertTrue(change.isModifyOperation());
        assertValidChangeObject(mNote, change);

        JSONObject diff = change.toJSONObject().getJSONObject("v");

        String expected = "{\"tags\":{\"v\":[],\"o\":\"+\"},\"deleted\":{\"v\":false,\"o\":\"+\"},\"title\":{\"v\":\"Hello world\",\"o\":\"+\"}}";
        assertEquals(expected, diff.toString());

    }

    public void testDeletePayload()
    throws Exception {

        mNote.save();

        Change change = new Change(Change.OPERATION_REMOVE, mNote);

        assertTrue(change.isRemoveOperation());
        assertValidChangeObject(mNote, change);

    }

    public void testChangeWithFullObjectDataPaylaod()
    throws Exception {

        Change change = new Change(Change.OPERATION_MODIFY, mNote);
        change.setSendFullObject(true);

        assertValidChangeObject(mNote, change);
        assertEquals(mNote.getDiffableValue().toString(), change.toJSONObject().getJSONObject("d").toString());
    }

    public static void assertValidChangeObject(Syncable object, Change change)
    throws Exception {

        JSONObject changeJSON = change.toJSONObject();

        assertNotNull("Change missing ccid", changeJSON.optString("ccid"));
        assertNotNull("Change missing operation key `o`", changeJSON.optString("o"));
        assertEquals("Change id is incorrect", object.getSimperiumKey(), changeJSON.getString("id"));

        if (change.isRemoveOperation()) {
            assertFalse("Remove operation has a diff value", changeJSON.has("v"));
            assertFalse("Remove operation has sv", changeJSON.has("sv"));
        } else {
            assertNotNull("Modify operation has no patch", changeJSON.optJSONObject("v"));
            if (object.getVersion() > 0) {
                assertEquals("Modify change did not have sv", (int) object.getVersion(), (int) changeJSON.getInt("sv"));
            }
        }
    }

}