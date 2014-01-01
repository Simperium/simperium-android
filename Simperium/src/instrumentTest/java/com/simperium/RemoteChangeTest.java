package com.simperium.client;

import com.simperium.client.RemoteChange;

import com.simperium.test.MockBucket;

import com.simperium.util.RemoteChangesUtil;

import com.simperium.models.Note;

import org.json.JSONObject;

import junit.framework.TestCase;

import static android.test.MoreAsserts.*;

public class RemoteChangeTest extends TestCase {

    public void testParseRemoteAddOperation()
    throws Exception {
        String changeString = "{\"cv\":\"mock-cv\",\"ccids\":[\"abc\"],\"ev\":1, \"id\":\"mock\",\"clientid\":\"mock-client\",\"o\":\"M\",\"v\":{}}";
        JSONObject changeJSON = new JSONObject(changeString);

        RemoteChange change = RemoteChange.buildFromMap(changeJSON);

        assertTrue(change.isAddOperation());
        assertFalse(change.isModifyOperation());
        assertFalse(change.isRemoveOperation());

    }

    public void testParseRemoteDeleteOperation()
    throws Exception {
        String changeString = "{\"cv\":\"mock-cv\",\"ccids\":[\"abc\"],\"ev\":2, \"id\":\"mock\",\"clientid\":\"mock-client\",\"o\":\"-\"}";
        JSONObject changeJSON = new JSONObject(changeString);

        RemoteChange change = RemoteChange.buildFromMap(changeJSON);
        assertTrue(change.isRemoveOperation());
        assertFalse(change.isAddOperation());
        assertFalse(change.isModifyOperation());
    }

    public void testParseRemoteModifyOperation()
    throws Exception {
        String changeString = "{\"cv\":\"mock-cv\",\"ccids\":[\"abc\"],\"ev\":2,\"sv\":1,\"id\":\"mock\",\"clientid\":\"mock-client\",\"o\":\"M\",\"v\":{}}";
        JSONObject changeJSON = new JSONObject(changeString);

        RemoteChange change = RemoteChange.buildFromMap(changeJSON);

        assertTrue(change.isModifyOperation());
        assertFalse(change.isRemoveOperation());
        assertFalse(change.isAddOperation());
    }

    /**
     * first we need an existing object
     */
    public void testCatchInvalidPatch()
    throws Exception {
        MockBucket<Note> notes = MockBucket.buildBucket(new Note.Schema());
        // insert a mock ghost
        Note note = notes.newObject("mock");
        note.setTitle("my hovercraft is full of eels");
        note.save();

        // This change has an invalid diff-match-path value
        String changeJSON = "{\"cv\":\"mock-cv\",\"ccids\":[\"abc\"],\"ev\":2,\"sv\":1,\"id\":\"mock\",\"clientid\":\"mock-client\",\"o\":\"M\",\"v\":{\"title\":{\"v\":\"=14\\t-1\\t+wa\\t=10\",\"o\":\"d\"}}}";
        RemoteChange change = RemoteChange.buildFromMap(new JSONObject(changeJSON));

        boolean caught = false;
        try {
            change.apply(note);
        } catch (RemoteChangeInvalidException e) {
            caught = true;
            assertEquals("Invalid patch", e.getMessage());
            assertAssignableFrom(IllegalArgumentException.class, e.getCause());
        }

        assertTrue("RemoteChangeInvalidException should have been caught", caught);

    }

    public void testMergeLocalModifications()
    throws Exception {

        MockBucket<Note> notes = MockBucket.buildBucket(new Note.Schema());

        // create a note and sync it
        Note note = notes.newObject("mock");
        note.setContent("Line 1");
        note.save();

        // create a 3rd party modification
        JSONObject external = new JSONObject(note.getDiffableValue().toString());
        external.put("content", "Line 1\nLine 2\n");

        // make a local modification
        note.setContent("Line 1\nLine 3\n");

        // build remote change based on 3rd party modification
        RemoteChange change = RemoteChangesUtil.buildRemoteChange(note, external);

        // apply the remote change to the object
        change.apply(note);

        // the content field sould be a merge of the two changes
        assertEquals("Line 1\nLine 2\nLine 3\n", note.getContent());

    }

}