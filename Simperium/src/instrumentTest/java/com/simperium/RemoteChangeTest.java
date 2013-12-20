package com.simperium.client;

import com.simperium.client.RemoteChange;

import com.simperium.test.MockBucket;

import com.simperium.models.Note;

import org.json.JSONObject;

import junit.framework.TestCase;

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

}