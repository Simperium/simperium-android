package com.simperium;

import com.simperium.client.Change;
import com.simperium.client.Bucket;
import com.simperium.client.Syncable;
import com.simperium.client.Ghost;

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

        Ghost ghost = mBucket.getGhost(mNote.getSimperiumKey());
        JSONObject object = mNote.getDiffableValue();
        assertTrue(change.isModifyOperation());
        assertValidChangeObject(mNote, ghost, change);


        JSONObject diff = change.toJSONObject(object, ghost).getJSONObject("v");

        String expected = "{\"tags\":{\"o\":\"+\",\"v\":[]},\"deleted\":{\"o\":\"+\",\"v\":false},\"title\":{\"o\":\"+\",\"v\":\"Hello world\"}}";
        assertEquals(expected, diff.toString());

    }

    public void testDeletePayload()
    throws Exception {

        mNote.save();

        Change change = new Change(Change.OPERATION_REMOVE, mNote);
        Ghost ghost = mBucket.getGhost(mNote.getSimperiumKey());
        assertTrue(change.isRemoveOperation());
        assertValidChangeObject(mNote, ghost, change);

    }

    public void testChangeWithFullObjectDataPaylaod()
    throws Exception {

        Change change = new Change(Change.OPERATION_MODIFY, mNote);
        change.setSendFullObject(true);

        Ghost ghost = mBucket.getGhost(mNote.getSimperiumKey());
        JSONObject object = mNote.getDiffableValue();
        assertValidChangeObject(mNote, ghost, change);
        assertEquals(mNote.getDiffableValue().toString(), change.toJSONObject(object, ghost).getJSONObject("d").toString());
    }

    public static void assertValidChangeObject(Syncable object, Ghost ghost, Change change)
    throws Exception {

        JSONObject changeJSON = change.toJSONObject(object.getDiffableValue(), ghost);

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