package com.simperium.testapp;

import com.simperium.util.JSONDiff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import java.util.Set;
import java.util.Collection;

import junit.framework.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class JSONDiffTest extends TestCase {

	JSONDiff jsondiff;

	public void setUp(){
		jsondiff = new JSONDiff();
	}

    public void testCommonPrefix()
    throws Exception {
        assertEquals(3, jsondiff.commonPrefix(array(1,2,3), array(1,2,3,4)));
        assertEquals(1, jsondiff.commonPrefix(array(1), array(1,2,3,4)));
        assertEquals(0, jsondiff.commonPrefix(array(2,3), array(1,2,3,4)));
    }

    public void testCommonSuffix()
    throws Exception {
        assertEquals(3, jsondiff.commonSuffix(array(0,2,3,4), array(1,2,3,4)));
        assertEquals(1, jsondiff.commonSuffix(array(1,4), array(1,2,3,4)));
        assertEquals(0, jsondiff.commonSuffix(array(1,4,5), array(1,2,3,4)));
    }

    public void testReplace()
    throws Exception {
		Map<String,Object> replaced = object("a","b");
		Map<String,Object> expected = op(JSONDiff.OPERATION_REPLACE, replaced);
		Map<String,Object> diff = jsondiff.diff(list(1), replaced);

		assertEquals(expected, diff);
	}

    public void testListAppend()
    throws Exception {
		// Buildint Java representation of {"o": "L", "v": {"1": {"o":"+", "v":4}}}
		List<Object> origin = list(1);
		List<Object> target = list(1,4);
		Map<String,Object> expected = list_op(object(
			"1",
			op(JSONDiff.OPERATION_INSERT, new Integer(4))
		));

		// Generating diff of [1] and [1,4]
		Map<String,Object> diff = jsondiff.diff(origin, target);

		assertEquals(expected, diff);
		List<Object> transformed = jsondiff.apply(origin, (Map<String,Object>)expected.get(JSONDiff.DIFF_VALUE_KEY));
		assertEquals(target, transformed);
	}

    public void testListRemoveLast()
    throws Exception {
		Map<String,Map>  diffs = new HashMap<String,Map>();
		diffs.put("1", op(JSONDiff.OPERATION_REMOVE));
		Map<String,Object> expected = list_op(diffs);

		List<Object> origin = list(1,4);
		List<Object> target = list(1);
		Map<String,Object> diff = jsondiff.diff(origin, target);

		assertEquals(expected, diff);
		List<Object> transformed = jsondiff.apply(origin, (Map<String,Object>)expected.get(JSONDiff.DIFF_VALUE_KEY));
		assertEquals(target, transformed);

	}

    public void testListRemoveFirst()
    throws Exception {
		Map<String,Object> diffs = new HashMap<String,Object>();
		diffs.put("0", op(JSONDiff.OPERATION_REMOVE));
		Map<String,Object> expected = list_op(diffs);
		List<Object> origin = list(1,4);
		List<Object> target = list(4);

		Map<String,Object> diff = jsondiff.diff(origin, target);
		assertEquals(expected, diff);
		List<Object> transformed = jsondiff.apply(origin, (Map<String,Object>)expected.get(JSONDiff.DIFF_VALUE_KEY));
		assertEquals(target, transformed);

	}

    public void testListRemove2Items()
    throws Exception {
		Map<String,Object> diffs = new HashMap<String,Object>();
		diffs.put("0", op(JSONDiff.OPERATION_REPLACE, new Integer(2)));
		diffs.put("1", op(JSONDiff.OPERATION_REMOVE));
		diffs.put("2", op(JSONDiff.OPERATION_REMOVE));

		Map<String,Object> expected = list_op(diffs);
		List<Object> origin = list(1,2,3,4);
		List<Object> target = list(2,4);
		Map<String,Object> diff = jsondiff.diff(origin, target);
		assertEquals(expected, diff);
		List<Object> transformed = jsondiff.apply(origin, (Map<String,Object>)expected.get(JSONDiff.DIFF_VALUE_KEY));
		assertEquals(target, transformed);
	}

    public void testListRemoveFirstInsertMiddleAppend()
    throws Exception {
		Map<String,Object> diffs = new HashMap<String,Object>();
		diffs.put("0", op(JSONDiff.OPERATION_REPLACE, new Integer(2)));
		diffs.put("1", op(JSONDiff.OPERATION_REPLACE, new Integer(6)));
		diffs.put("4", op(JSONDiff.OPERATION_INSERT, new Integer(5)));
		Map<String,Object> expected = list_op(diffs);
		List<Object> origin = list(1,2,3,4);
		List<Object> target = list(2,6,3,4,5);

		Map<String,Object> diff = jsondiff.diff(origin, target);
		assertEquals(expected, diff);
		List<Object> transformed = jsondiff.apply(origin, (Map<String,Object>)expected.get(JSONDiff.DIFF_VALUE_KEY));
		assertEquals(target, transformed);

	}

    public void testObjectChangeKeyString()
    throws Exception {
		Map<String,Object> origin = object("a","b");
		Map<String,Object> target = object("a","c");
		Map<String,Object> diffs = new HashMap<String,Object>();
		Map<String,Object> expected = object_op(object(
			"a",
			op(JSONDiff.OPERATION_DIFF, "-1\t+c")
		));
		Map<String,Object> diff = jsondiff.diff(origin, target);
		assertEquals(expected, diff);
		Map<String,Object> transformed = jsondiff.apply(origin, (Map<String,Object>)expected.get(JSONDiff.DIFF_VALUE_KEY));
		assertEquals(target, transformed);
	}

    public void testObjectChangekeyStringToInt()
    throws Exception {
		Map<String,Object> origin = object("a","b");
		Map<String,Object> target = object("a", new Integer(7));
		Map<String,Object> diffs = new HashMap<String,Object>();
		Map<String,Object> expected = object_op(object(
			"a",
			op(JSONDiff.OPERATION_REPLACE, target.get("a"))
		));

		Map<String,Object> diff = jsondiff.diff(origin, target);
		assertEquals(expected, diff);
		Map<String,Object> transformed = jsondiff.apply(origin, (Map<String,Object>)expected.get(JSONDiff.DIFF_VALUE_KEY));
		assertEquals(target, transformed);
	}

    public void testObjectAddKey()
    throws Exception {
		Map<String,Object> origin = object("a","b");
		Map<String,Object> target = object("a","b","e","d");
		Map<String,Object> expected = object_op(object(
			"e",
			op(JSONDiff.OPERATION_INSERT, target.get("e"))
		));

		Map<String,Object> diff = jsondiff.diff(origin,target);
		assertEquals(expected, diff);
		Map<String,Object> transformed = jsondiff.apply(origin, (Map<String,Object>)expected.get(JSONDiff.DIFF_VALUE_KEY));
		assertEquals(target, transformed);
	}

    public void testObjectRemoveKey()
    throws Exception {
		Map<String,Object> origin = object("a","b","e","d");
		Map<String,Object> target = object("a","b");
		Map<String,Object> expected = object_op(object(
			"e",
			op(JSONDiff.OPERATION_REMOVE)
		));

		Map<String,Object> diff = jsondiff.diff(origin,target);
		assertEquals(expected, diff);
		Map<String,Object> transformed = jsondiff.apply(origin, (Map<String,Object>)expected.get(JSONDiff.DIFF_VALUE_KEY));
		assertEquals(target, transformed);
	}

    public void testObjectAppendListItem()
    throws Exception {
		Map<String,Object> origin = object("a", list(1));
		Map<String,Object> target = object("a", list(1,4));
		Map<String,Object> expected = object_op(
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

		Map<String,Object> diff = jsondiff.diff(origin, target);
		Map<String,Object> transformed = jsondiff.apply(origin, (Map<String,Object>)expected.get(JSONDiff.DIFF_VALUE_KEY));
		assertEquals(target, transformed);
	}

    public void testSliceJSONArray()
    throws Exception {
        JSONArray a = new JSONArray("[1,\"a\",3,4,5]");
        JSONArray sub = jsondiff.sliceJSONArray(a, 1, 5);

        JSONArray expected = new JSONArray("[\"a\",3,4,5]");
        assertEquals(expected, sub);

    }

    // public void testNestedArrayAndHash(){
    //     // {"a":[{"a":"b","c":"d"},[{"a":"b"},"2","3","4"]]}}
    //     Map<String,Object> origin = object(
    //         "a",
    //         (Object)list(
    //             (Object)object("a", "b", "c", "d")
    //         )
    //     );
    //     // {"a":[{"a":"b","c":"e"}, ["1",{"a":"b"},"2","3","4","5"]]}
    //     Map<String,Object> target = object(
    //         "a",
    //         (Object)list(
    //             (Object)object("a", "b", "c", "e") // { "a":"b", "c":"d" }
    //         )
    //     );
    //     Map<String,Object> expected = object_op(
    //         (Object)object( "a", list_op(
    //             (Object)object(
    //                 "0", (Object)object_op( object("c", op(JSONDiff.OPERATION_DIFF, "-1\t+e")) )
    //             )
    //         ))
    //     );
    //     assertEquals(expected, jsondiff.diff(origin,target));
    //     assertEquals(target, jsondiff.apply((Object)origin,expected));
    // }

    // public void testRemoveArrayItemsThatAreSame(){
    //     List<String> origin = list("a", "a", "a");
    //     List<String> target = list("a");
    //     Map<String,Object> operations = object("1", op(JSONDiff.OPERATION_REMOVE));
    //     operations.put("2", op(JSONDiff.OPERATION_REMOVE));
    //     Map<String,Object> expected = list_op(operations);
    //     assertEquals(expected, jsondiff.diff(origin,target));
    //     assertEquals(target, jsondiff.apply((Object) origin, expected));
    // }

    // public void testAddArrayItemsThatAreSame(){
    //     List<String> origin = list("a");
    //     List<String> target = list("a", "a", "a");
    //     Map<String,Object> operations = object("1", op(JSONDiff.OPERATION_INSERT, "a"));
    //     operations.put("2", op(JSONDiff.OPERATION_INSERT, "a"));
    //     Map<String,Object> expected = list_op(operations);
    //     assertEquals(expected, jsondiff.diff(origin,target));
    //     assertEquals(target, jsondiff.apply((Object) origin, expected));
    // }

	/*
	 * Convenient object building methods for test use
	 *
	 **/
	public List list(int... args){
		List<Integer> a = new ArrayList<Integer>();
		for (int i : args ) {
			a.add(new Integer(i));
		}
		return a;
	}

	public List list(Object... args){
		List<Object> a = new ArrayList<Object>();
		for (Object i : args ) {
			a.add(i);
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

	public Map<String,Object> list_op(Object value){
		Map<String,Object> o = op(JSONDiff.OPERATION_LIST);
		o.put(JSONDiff.DIFF_VALUE_KEY, value);
		return o;
	}

	public Map<String,Object> object_op(Object value){
		Map<String,Object> o = op(JSONDiff.OPERATION_OBJECT);
		o.put(JSONDiff.DIFF_VALUE_KEY, value);
		return o;
	}

	public Map<String,Object> object(String... args){
		Map <String,Object> o = new HashMap<String,Object>();
		for(int i=0;i<args.length;i+=2){
			o.put(args[i], args[i+1]);
		}
		return o;
	}

	public Map<String,Object> object(String key, Object value){
		Map <String,Object> o = new HashMap<String,Object>();
		o.put(key, value);
		return o;
	}

	public Map<String,Object> op(String operation, Object value){
		Map<String,Object> o = op(operation);
		o.put(JSONDiff.DIFF_VALUE_KEY, value);
		return o;
	}
	public Map<String,Object> op(String operation){
		Map<String,Object> o = new HashMap<String,Object>();
		o.put(JSONDiff.DIFF_OPERATION_KEY, operation);
		return o;
	}

}
