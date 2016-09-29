package com.simperium;

import junit.framework.TestCase;

import org.json.JSONObject;
import org.json.JSONException;

public class JSONIntTest extends TestCase {

    JSONObject object;

    protected void setUp() throws JSONException {
        object = new JSONObject("{ \"id\" : 2147515232 }");
    }

    public void testParseJSONMaxInt() throws Exception {
        int id = object.getInt("id");
        // Seems to pin the value to something around MIN_INT?
        assertEquals(-2147452064, id);
    }

    public void testParseJSONMaxIntAsString() throws Exception {
        String id = object.optString("id");
        assertEquals("2147515232", id);
    }

    public void testJSONDeepCopy() throws Exception {
        
    }

}