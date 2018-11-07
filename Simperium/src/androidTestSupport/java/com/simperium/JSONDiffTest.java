package com.simperium;

import com.simperium.util.JSONDiff;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JSONDiffTest extends TestCase {


    public void setUp(){
        JSONDiff.enableArrayDiff = false;
    }

    public void testStringDiff()
    throws Exception {

        JSONObject diff = JSONDiff.diff("Hello world", "Hello World!");

        String expected = "=6\t-1\t+W\t=4\t+!";
        assertEquals(expected, diff.getString("v"));
    }

    public void testEmojiStringDiff()
    throws Exception {

        String origin = "This 💩 stinks";
        String emoji = "This stinks less";

        JSONObject diff = JSONDiff.diff(origin, emoji);

        assertEquals("=5\t-3\t=6\t+ less", diff.getString("v"));
        assertEquals(emoji, JSONDiff.apply(origin, diff.getString("v")));

    }


    public void testCommonPrefix()
    throws Exception {
        assertEquals(3, JSONDiff.commonPrefix(list(1,2,3), list(1,2,3,4)));
        assertEquals(1, JSONDiff.commonPrefix(list(1), list(1,2,3,4)));
        assertEquals(0, JSONDiff.commonPrefix(list(2,3), list(1,2,3,4)));
    }

    public void testCommonSuffix()
    throws Exception {
        assertEquals(3, JSONDiff.commonSuffix(list(0,2,3,4), list(1,2,3,4)));
        assertEquals(1, JSONDiff.commonSuffix(list(1,4), list(1,2,3,4)));
        assertEquals(0, JSONDiff.commonSuffix(list(1,4,5), list(1,2,3,4)));
    }

    public void testReplace()
    throws Exception {
        JSONObject replaced = object("a","b");
        JSONObject expected = op(JSONDiff.OPERATION_REPLACE, replaced);
        JSONObject diff = JSONDiff.diff(list(1), replaced);

        assertEquals(expected, diff);
    }

    public void testListAppend()
    throws Exception {
        JSONDiff.enableArrayDiff = true;
        // Buildint Java representation of {"o": "L", "v": {"1": {"o":"+", "v":4}}}
        JSONArray origin = list(1);
        JSONArray target = list(1,4);

        JSONObject expected = list_op(object(
            "1",
            op(JSONDiff.OPERATION_INSERT, new Integer(4))
        ));

        // Generating diff of [1] and [1,4]
        JSONObject diff = JSONDiff.diff(origin, target);

        assertEquals(expected, diff);
        JSONArray transformed = JSONDiff.apply(origin, expected.getJSONObject(JSONDiff.DIFF_VALUE_KEY));
        assertEquals(target, transformed);
    }

    public void testListRemoveLast()
    throws Exception {
        JSONDiff.enableArrayDiff = true;

        JSONObject diffs = new JSONObject();
        diffs.put("1", op(JSONDiff.OPERATION_REMOVE));
        JSONObject expected = list_op(diffs);

        JSONArray origin = list(1,4);
        JSONArray target = list(1);
        JSONObject diff = JSONDiff.diff(origin, target);

        assertEquals(expected, diff);
        JSONArray transformed = JSONDiff.apply(origin, expected.getJSONObject(JSONDiff.DIFF_VALUE_KEY));
        assertEquals(target, transformed);

    }

    public void testListRemoveFirst()
    throws Exception {
        JSONDiff.enableArrayDiff = true;

        JSONObject diffs = new JSONObject();
        diffs.put("0", op(JSONDiff.OPERATION_REMOVE));
        JSONObject expected = list_op(diffs);
        JSONArray origin = list(1,4);
        JSONArray target = list(4);

        JSONObject diff = JSONDiff.diff(origin, target);
        assertEquals(expected, diff);
        JSONArray transformed = JSONDiff.apply(origin, expected.getJSONObject(JSONDiff.DIFF_VALUE_KEY));
        assertEquals(target, transformed);

    }

    public void testListRemove2Items()
    throws Exception {
        JSONDiff.enableArrayDiff = true;

        JSONObject diffs = new JSONObject();
        diffs.put("0", op(JSONDiff.OPERATION_REPLACE, new Integer(2)));
        diffs.put("1", op(JSONDiff.OPERATION_REMOVE));
        diffs.put("2", op(JSONDiff.OPERATION_REMOVE));

        JSONObject expected = list_op(diffs);
        JSONArray origin = list(1,2,3,4);
        JSONArray target = list(2,4);
        JSONObject diff = JSONDiff.diff(origin, target);
        JSONArray transformed = JSONDiff.apply(origin, expected.getJSONObject(JSONDiff.DIFF_VALUE_KEY));

        assertEquals(expected, diff);
        assertEquals(target, transformed);
    }

    public void testListRemoveFirstInsertMiddleAppend()
    throws Exception {
        JSONDiff.enableArrayDiff = true;

        JSONObject diffs = new JSONObject();
        diffs.put("0", op(JSONDiff.OPERATION_REPLACE, new Integer(2)));
        diffs.put("1", op(JSONDiff.OPERATION_REPLACE, new Integer(6)));
        diffs.put("4", op(JSONDiff.OPERATION_INSERT, new Integer(5)));
        JSONObject expected = list_op(diffs);
        JSONArray origin = list(1,2,3,4);
        JSONArray target = list(2,6,3,4,5);

        JSONObject diff = JSONDiff.diff(origin, target);
        assertEquals(expected, diff);
        JSONArray transformed = JSONDiff.apply(origin, expected.getJSONObject(JSONDiff.DIFF_VALUE_KEY));
        assertEquals(target, transformed);

    }

    public void testObjectChangeKeyString()
    throws Exception {
        JSONObject origin = object("a","b");
        JSONObject target = object("a","c");
        JSONObject diffs = new JSONObject();
        JSONObject expected = object_op(object(
            "a",
            op(JSONDiff.OPERATION_DIFF, "-1\t+c")
        ));
        JSONObject diff = JSONDiff.diff(origin, target);
        assertEquals(expected, diff);
        JSONObject transformed = JSONDiff.apply(origin, expected.getJSONObject(JSONDiff.DIFF_VALUE_KEY));
        assertEquals(target, transformed);
    }

    public void testObjectChangekeyStringToInt()
    throws Exception {
        JSONObject origin = object("a","b");
        JSONObject target = object("a", new Integer(7));
        JSONObject diffs = new JSONObject();
        JSONObject expected = object_op(object(
            "a",
            op(JSONDiff.OPERATION_REPLACE, target.get("a"))
        ));

        JSONObject diff = JSONDiff.diff(origin, target);
        assertEquals(expected, diff);
        JSONObject transformed = JSONDiff.apply(origin, expected.getJSONObject(JSONDiff.DIFF_VALUE_KEY));
        assertEquals(target, transformed);
    }

    public void testObjectAddKey()
    throws Exception {
        JSONObject origin = object("a","b");
        JSONObject target = object("a","b","e","d");
        JSONObject expected = object_op(object(
            "e",
            op(JSONDiff.OPERATION_INSERT, target.get("e"))
        ));

        JSONObject diff = JSONDiff.diff(origin,target);
        JSONObject transformed = JSONDiff.apply(origin, expected.getJSONObject(JSONDiff.DIFF_VALUE_KEY));

        assertEquals(expected, diff);
        assertEquals(target, transformed);
    }

    public void testObjectRemoveKey()
    throws Exception {
        JSONObject origin = object("a","b","e","d");
        JSONObject target = object("a","b");
        JSONObject expected = object_op(object(
            "e",
            op(JSONDiff.OPERATION_REMOVE)
        ));

        JSONObject diff = JSONDiff.diff(origin, target);
        JSONObject transformed = JSONDiff.apply(origin, expected.getJSONObject(JSONDiff.DIFF_VALUE_KEY));

        assertEquals(expected, diff);
        assertEquals(target, transformed);
    }

    public void testObjectAppendListItem()
    throws Exception {

        JSONObject origin = object("a", list(1));
        JSONObject target = object("a", list(1,4));
        JSONObject expected = object_op(
            object(
                "a",
                list_op(
                    object(
                        "1",
                        op(
                            JSONDiff.OPERATION_INSERT,
                            new Integer(4)
                        )
                    )
                )
            )
        );

        JSONObject diff = JSONDiff.diff(origin, target);
        JSONObject transformed = JSONDiff.apply(origin, expected.getJSONObject(JSONDiff.DIFF_VALUE_KEY));
        assertEquals(target, transformed);
    }

    public void testSliceJSONArray()
    throws Exception {
        JSONArray a = new JSONArray("[1,\"a\",3,4,5]");
        JSONArray sub = JSONDiff.sliceJSONArray(a, 1, 5);

        JSONArray expected = new JSONArray("[\"a\",3,4,5]");
        assertEquals(expected, sub);

    }

    public void testNestedArrayAndHash()
    throws Exception {

        JSONDiff.enableArrayDiff = true;

        // {"a":[{"a":"b","c":"d"},[{"a":"b"},"2","3","4"]]}}
        JSONObject origin = object(
            "a",
            (Object)list(
                (Object)object("a", "b", "c", "d")
            )
        );

        // {"a":[{"a":"b","c":"e"}, ["1",{"a":"b"},"2","3","4","5"]]}
        JSONObject target = object(
            "a",
            (Object)list(
                (Object)object("a", "b", "c", "e") // { "a":"b", "c":"d" }
            )
        );

        JSONObject expected = object_op(
            (Object)object( "a", list_op(
                (Object)object(
                    "0", (Object)object_op( object("c", op(JSONDiff.OPERATION_DIFF, "-1\t+e")) )
                )
            ))
        );
        assertEquals(expected, JSONDiff.diff(origin,target));
        assertEquals(target, (JSONObject) JSONDiff.apply((Object) origin, expected));
    }

    public void testRemoveArrayItemsThatAreSame()
    throws Exception {
        JSONDiff.enableArrayDiff = true;

        JSONArray origin = list("a", "a", "a");
        JSONArray target = list("a");
        JSONObject operations = object("1", op(JSONDiff.OPERATION_REMOVE));
        operations.put("2", op(JSONDiff.OPERATION_REMOVE));
        JSONObject expected = list_op(operations);
        assertEquals(expected, JSONDiff.diff(origin,target));
        assertEquals(target, JSONDiff.apply((Object) origin, expected));
    }

    public void testAddArrayItemsThatAreSame()
    throws Exception {
        JSONDiff.enableArrayDiff = true;
        JSONArray origin = list("a");
        JSONArray target = list("a", "a", "a");
        JSONObject operations = object("1", op(JSONDiff.OPERATION_INSERT, "a"));
        operations.put("2", op(JSONDiff.OPERATION_INSERT, "a"));
        JSONObject expected = list_op(operations);
        assertEquals(expected, JSONDiff.diff(origin,target));
        assertEquals(target, JSONDiff.apply((Object) origin, expected));
    }

    public void testObjectEqualityWithListsAndUnsortedKeys()
    throws Exception {
        // same k/v but different order
        // a = { "a":"b", "c":"d" }
        // b = { "c":"d", "a":"b" }
        JSONObject a = object("a", "b", "c", "d");
        JSONObject b = object("c", "d", "a", "b");
        JSONObject origin = object("a", list(a));
        JSONObject target = object("a", list(b));
        JSONObject diff = JSONDiff.diff(origin, target);

        assertEquals(new JSONObject(), diff);
    }

    public void testTransformStringDiff()
    throws Exception {

        String origin = "Hello World.";
        String target = "Hello world. Thanks. Good bye.";

        JSONObject diff_1 = JSONDiff.diff(origin, "Hello world.");
        JSONObject diff_2 = JSONDiff.diff(origin, "Hello World. Thanks. Good bye.");

        JSONObject transformed = JSONDiff.transform(diff_2.getString("v"), diff_1.getString("v"), origin);

        assertEquals(target, JSONDiff.apply(JSONDiff.apply(origin, diff_1), transformed));

    }

    public void testInvalidStringTransformThrowsException()
            throws Exception {
        String origin = "Line 1\nLine 2\nReplace me";
        try
        {
            JSONObject transformed = JSONDiff.transform(
                    "=14\t+Before%0A\t=10\t+%0AAfter",
                    "=14\t-10\t+BYE",
                    origin
            );
            fail("Patch transform should have failed.");
        }
        catch(Exception e)
        {
            // Test passed
        }
    }

    public void testTransformObjectDiffChangeRemovedKey()
    throws Exception {

        JSONObject origin = object("a", 1);
        JSONObject target = object("a", 2);

        target.put("b", 3);

        JSONObject diff_1 = JSONDiff.diff(origin, object("b", 3));
        JSONObject diff_2 = JSONDiff.diff(origin, target);

        JSONObject transformed = JSONDiff.transform(diff_2.getJSONObject("v"), diff_1.getJSONObject("v"), origin);
        JSONObject applied = JSONDiff.apply(JSONDiff.apply(origin, diff_1.getJSONObject("v")), transformed);
        assertEquals(target.get("b"), applied.get("b"));
        assertEquals(target.get("a"), applied.get("a"));
    }

    public void testTransformBothAdd()
    throws Exception {

        JSONObject origin = object("a", "b");
        JSONObject target = object("a", "b", "c", "d", "e", "f");

        JSONObject diff_1 = JSONDiff.diff(origin, object("a", "b", "c", "d"));
        JSONObject diff_2 = JSONDiff.diff(origin, target);

        JSONObject transformed = JSONDiff.transform(diff_2.getJSONObject("v"), diff_1.getJSONObject("v"), origin);

        // transformed diff should not add c key since diff_1 does
        assertFalse(diff_2.has("c"));
        assertEquals(object("a", "b", "e", "f"), JSONDiff.apply(origin, transformed));

    }

    public void testTransformBothRemove()
    throws Exception {

        JSONObject origin = object("a", "b", "c", "d");

        // remove the a key
        JSONObject diff_1 = JSONDiff.diff(origin, object("c", "d"));
        // remove a add e
        JSONObject diff_2 = JSONDiff.diff(origin, object("c", "d", "e", "f"));

        JSONObject transformed = JSONDiff.transform(diff_2.getJSONObject("v"), diff_1.getJSONObject("v"), origin);

        // transformed diff will not remove a key since diff_1 does
        assertEquals(object("a", "b", "c", "d", "e", "f"), JSONDiff.apply(origin, transformed));

    }


    /*
     * Convenient object building methods for test use
     *
     **/
    public JSONArray list(Object... args)
    throws JSONException {
        JSONArray a = new JSONArray();
        for (Object i : args) {
            a.put(i);
        }
        return a;
    }

    public JSONArray array(Object ... args)
    throws Exception {
        JSONArray a = new JSONArray();
        for (Object o : args) {
            a.put(o);
        }
        return a;
    }

    public JSONObject list_op(Object value)
    throws JSONException {
        JSONObject o = op(JSONDiff.OPERATION_LIST);
        o.put(JSONDiff.DIFF_VALUE_KEY, value);
        return o;
    }

    public JSONObject object_op(Object value)
    throws JSONException {
        JSONObject o = op(JSONDiff.OPERATION_OBJECT);
        o.put(JSONDiff.DIFF_VALUE_KEY, value);
        return o;
    }

    public JSONObject object(String... args)
    throws JSONException {
        JSONObject o = new JSONObject();
        for(int i=0; i<args.length; i+=2){
            o.put(args[i], args[i+1]);
        }
        return o;
    }

    public JSONObject object(String key, Object value)
    throws JSONException {
        JSONObject o = new JSONObject();
        o.put(key, value);
        return o;
    }

    public JSONObject op(String operation, Object value)
    throws JSONException {
        JSONObject o = op(operation);
        o.put(JSONDiff.DIFF_VALUE_KEY, value);
        return o;
    }

    public JSONObject op(String operation)
    throws JSONException {
        JSONObject o = new JSONObject();
        o.put(JSONDiff.DIFF_OPERATION_KEY, operation);
        return o;
    }

    public static void assertEquals(JSONObject a, JSONObject b) {
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(a.toString(), b.toString());
    }

    public static void assertEquals(JSONArray a, JSONArray b) {
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(a.toString(), b.toString());
    }

}
