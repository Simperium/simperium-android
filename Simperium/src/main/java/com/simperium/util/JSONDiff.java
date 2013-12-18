package com.simperium.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.Patch;

public class JSONDiff {

    static public boolean enableArrayDiff = false;

    static public final String TAG = "JSONDiff";

    public static final String DIFF_VALUE_KEY     = "v";
    public static final String DIFF_OPERATION_KEY = "o";

    public static final String OPERATION_OBJECT  = "O";
    public static final String OPERATION_LIST    = "L";
    public static final String OPERATION_INSERT  = "+";
    public static final String OPERATION_REMOVE  = "-";
    public static final String OPERATION_REPLACE = "r";
    public static final String OPERATION_DIFF    = "d";

    private static diff_match_patch dmp = new diff_match_patch();

    public static JSONObject diff(JSONArray a, JSONArray b)
    throws JSONException {

        JSONObject list_diff = new JSONObject();

        if (equals(a, b)) {
            return list_diff;
        }

        if (!enableArrayDiff){
            list_diff.put(DIFF_OPERATION_KEY, OPERATION_REPLACE);
            list_diff.put(DIFF_VALUE_KEY, b);
            return list_diff;
        }

        list_diff.put(DIFF_OPERATION_KEY, OPERATION_LIST);
        JSONObject diffs = new JSONObject();

        list_diff.put(DIFF_VALUE_KEY, diffs);

        int size_a = a.length();
        int size_b = b.length();

        int prefix_length = commonPrefix(a, b);

        // remove the prefixes
        a = sliceJSONArray(a, prefix_length, size_a);
        b = sliceJSONArray(b, prefix_length, size_b);

        // recalculate the sizes
        size_a -= prefix_length;
        size_b -= prefix_length;


        int suffix_length = commonSuffix(a, b);

        size_a -= suffix_length;
        size_b -= suffix_length;

        a = sliceJSONArray(a, 0, size_a);
        b = sliceJSONArray(b, 0, size_b);

        int max = Math.max(size_a, size_b);

        for (int i=0; i<max; i++) {
            String index = new Integer(i+prefix_length).toString();
            if(i<size_a && i<size_b){
                // both lists have index
                // if values aren't equal add to diff
                if (!equals(a.get(i), b.get(i))) {
                    diffs.put(index, diff(a.get(i), b.get(i)));
                }
            } else if(i<size_a){
                // b doesn't have it remove from a
                JSONObject diff = new JSONObject();
                diff.put(DIFF_OPERATION_KEY, OPERATION_REMOVE);
                diffs.put(index, diff);
            } else if(i<size_b){
                // a doesn't have, b does so add it
                JSONObject diff = new JSONObject();
                diff.put(DIFF_OPERATION_KEY, OPERATION_INSERT);
                diff.put(DIFF_VALUE_KEY, b.get(i));
                diffs.put(index, diff);
            }
        }

        return list_diff;
	}

    public static JSONObject diff(JSONObject a, JSONObject b)
    throws JSONException {
        JSONObject diffs = new JSONObject();
        if (a == null || b == null) {
            return diffs;
        }

        if (equals(a, b)) {
            return diffs;
        }

        Iterator<String> keys = a.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (b.has(key)) {
                if (!equals(a.get(key), b.get(key))) {
                    diffs.put(key, diff(a.get(key), b.get(key)));
                }
            } else {
                JSONObject remove = new JSONObject();
                remove.put(DIFF_OPERATION_KEY, OPERATION_REMOVE);
                diffs.put(key, remove);
            }
        }

        keys = b.keys();
        while (keys.hasNext()) {
            String key = keys.next().toString();
            if (!a.has(key)) {
                JSONObject add = new JSONObject();
                add.put(DIFF_OPERATION_KEY, OPERATION_INSERT);
                add.put(DIFF_VALUE_KEY, b.get(key));
                diffs.put(key, add);
            }
        }

        JSONObject diff = new JSONObject();
        if (diffs.length() > 0) {
            diff.put(DIFF_OPERATION_KEY, OPERATION_OBJECT);
            diff.put(DIFF_VALUE_KEY, diffs);
        }

        return diff;
	}

    public static JSONObject diff(Object a, Object b)
    throws JSONException {
        JSONObject m = new JSONObject();
        if (a==null || b==null) {
            return m;
        }

        if (equals(a, b)) {
            return m;
        }

        Class a_class = a.getClass();
        Class b_class = b.getClass();

        if ( !a_class.isAssignableFrom(b_class) ) {
            m.put(DIFF_OPERATION_KEY, OPERATION_REPLACE);
            m.put(DIFF_VALUE_KEY, b);
            return m;
        }

        // a and b are the same type
        if (String.class.isInstance(a)) {
            // diff match patch
            return diff((String)a, (String)b);
        } else if(JSONObject.class.isInstance(a)){
            return diff((JSONObject) a, (JSONObject) b);
        } else if (JSONArray.class.isInstance(a)) {
            return diff((JSONArray) a, (JSONArray) b);
        } else {
            m.put(DIFF_OPERATION_KEY, OPERATION_REPLACE);
            m.put(DIFF_VALUE_KEY, b);
        }

        return m;
	}

    public static JSONObject diff(String origin, String target)
    throws JSONException {
        JSONObject m = new JSONObject();
        LinkedList diffs = dmp.diff_main(origin, target);
        if(diffs.size() > 2){
            dmp.diff_cleanupEfficiency(diffs);
        }
        if(diffs.size() > 0){
            m.put(DIFF_OPERATION_KEY, OPERATION_DIFF);
            m.put(DIFF_VALUE_KEY, dmp.diff_toDelta(diffs));
        }
        return m;
    }

    /**
     * For testing equality of two objects.
     */
    public static boolean equals(Object a, Object b) {

        // objects are not equal types
        if (!a.getClass().isAssignableFrom(b.getClass())) {
            return false;
        }

        if (JSONObject.class.isInstance(a)) {
            return equals((JSONObject) a, (JSONObject) b);
        } else if (JSONArray.class.isInstance(a)){
            return equals((JSONArray) a, (JSONArray) b);
        } else {
            return a.equals(b);
        }

    }

    public static boolean equals(JSONObject a, JSONObject b) {

        if (a == null || b == null) {
            return false;
        }

        try {
            // before iterating through keys make sure we have the same length
            if (a.length() != b.length()) {
                return false;
            }

            // make sure each key in a is the same as b
            Iterator<String> keys = a.keys();
            while (keys.hasNext()) {
                String key = keys.next();

                // b is missing the key, so they're not equal
                if (!b.has(key)) {
                    return false;
                }

                // a[key] does not equal b[key] so a and b are not equal
                if (!equals(a.get(key), b.get(key))) {
                    return false;
                }
            }
        } catch (JSONException e) {
            return false;
        }

        // since a and b have the same number of keys, and each value each key
        // in a is equal to that key in b, a and b are equal

        return true;
    }

    public static boolean equals(JSONArray a, JSONArray b) {

        if (a == null || b == null) {
            return false;
        }

        if (a.length() != b.length()) {
            return false;
        }

        try {
            for (int i=0; i<a.length(); i++) {
                if (!equals(a.get(i), b.get(i))) {
                    return false;
                }
            }
        } catch (JSONException e) {
            return false;
        }

        return true;

    }

    public static Object apply(Object origin, JSONObject patch)
    throws JSONException {
        String method = (String)patch.get(DIFF_OPERATION_KEY);
        if (method.equals(OPERATION_LIST)) {
            return apply((JSONArray) origin, patch.getJSONObject(DIFF_VALUE_KEY));
        } else if(method.equals(OPERATION_OBJECT)){
            return apply((JSONObject) origin, patch.getJSONObject(DIFF_VALUE_KEY));
        } else if(method.equals(OPERATION_DIFF)){
            return apply((String)origin, patch.getString(DIFF_VALUE_KEY));
        }
        return null;
    }

    public static JSONObject apply(JSONObject origin, JSONObject patch)
    throws JSONException {
        JSONObject transformed = deepCopy(origin);
        Iterator<String> keys = patch.keys();

        while (keys.hasNext()) {

            String key = keys.next();
            JSONObject operation = patch.getJSONObject(key);
            String method = operation.getString(DIFF_OPERATION_KEY);

            if (method.equals(OPERATION_INSERT) || method.equals(OPERATION_REPLACE)) {
                transformed.put(key, operation.get(DIFF_VALUE_KEY));
            } else if(method.equals(OPERATION_REMOVE)){
                // NOTE: setting the value to null actually removes the entry
                transformed.put(key, null);
            } else if(method.equals(OPERATION_OBJECT)){
                JSONObject child = transformed.getJSONObject(key);
                transformed.put(key, apply(child, operation.getJSONObject(DIFF_VALUE_KEY)));
            } else if(method.equals(OPERATION_LIST)) {
                JSONArray child = transformed.getJSONArray(key);
                transformed.put(key, apply(child, operation.getJSONObject(DIFF_VALUE_KEY)));
            } else if(method.equals(OPERATION_DIFF)){
                String child = transformed.getString(key);
                transformed.put(key, apply(child, operation.getString(DIFF_VALUE_KEY)));
            }

        }

        return transformed;
    }

    public static String apply(String origin, String patch){
        LinkedList<Diff> diffs = dmp.diff_fromDelta(origin, patch);
        LinkedList<Patch> patches = dmp.patch_make(origin, diffs);
        Object[] result = dmp.patch_apply(patches, origin);
        return (String)result[0];
    }

    public static JSONArray apply(JSONArray origin, JSONObject patch)
    throws JSONException {

        // copy JSON array to a List so we can
        List<Object> transformed = convertJSONArrayToList(origin);

        List<Integer> indexes = new ArrayList<Integer>();
        List<Integer> deleted = new ArrayList<Integer>();

        // iterate the keys on the patch
        Iterator<String> keys = patch.keys();
        while (keys.hasNext()){
            String key = keys.next();
            indexes.add(Integer.parseInt(key));
        }

        Collections.sort(indexes);
        for(Integer index : indexes){

            JSONObject operation = patch.getJSONObject(index.toString());
            String method = operation.getString(DIFF_OPERATION_KEY);

            int shift = 0;
            for(Integer x : deleted){
                if(x.intValue()<=index.intValue()) shift++;
            }

            int shifted_index = index.intValue() - shift;

            if (method.equals(OPERATION_INSERT)){
                transformed.add(shifted_index, operation.get(DIFF_VALUE_KEY));
            } else if(method.equals(OPERATION_REMOVE)){
                transformed.remove(shifted_index);
                deleted.add(index);
            } else if (method.equals(OPERATION_REPLACE)){
                transformed.set(shifted_index, operation.get(DIFF_VALUE_KEY));
            } else if(method.equals(OPERATION_LIST)){
                JSONArray list = (JSONArray) transformed.get(shifted_index);
                transformed.set(shifted_index, apply(list, operation.getJSONObject(DIFF_VALUE_KEY)));
            } else if(method.equals(OPERATION_OBJECT)){
                JSONObject obj = (JSONObject) transformed.get(shifted_index);
                transformed.set(shifted_index, apply(obj, operation.getJSONObject(DIFF_VALUE_KEY)));
            } else if(method.equals(OPERATION_DIFF)){
                String str = (String)transformed.get(shifted_index);
                transformed.set(shifted_index, apply(str, operation.getString(DIFF_VALUE_KEY)));
            }

        }

        return new JSONArray(transformed);
    }

    public static int commonPrefix(JSONArray a, JSONArray b) {
        int a_length = a.length();
        int b_length = b.length();
        int min_length = Math.min(a_length, b_length);
        int size = 0;
        for (int i=0; i<min_length; i++) {
            try {
                if (!equals(a.get(i), b.get(i)))
                    break;
            } catch (JSONException e) {
                return i;
            }
            size ++;
        }
        return size;
    }

    public static int commonSuffix(JSONArray a, JSONArray b) {
        int a_length = a.length();
        int b_length = b.length();
        int min_length = Math.min(a_length, b_length);
        if (min_length ==0) return 0;
        for (int i=0; i<min_length; i++) {
            try {
                if (!equals(a.get(a_length-i-1), b.get(b_length-i-1)))
                    return i;
            } catch (JSONException e) {
                return i;
            }
        }
        return min_length;
    }

    public static JSONArray sliceJSONArray(JSONArray a, int start, int end)
    throws JSONException {
        int length = a.length();
        if (end > length || start < 0) throw new java.lang.IndexOutOfBoundsException(
            String.format("indexes %d and %d not valid for array of length %d", start, end, length));

        JSONArray dest = new JSONArray();
        for (int i=start; i<end; i++) {
            dest.put(a.get(i));
        }

        return dest;
    }

    /**
     * Copy a hash
     */
    public static Map<String, Object> deepCopy(Map<String, Object> map){
        if (map == null) {
            return null;
        };
        Map<String,Object> copy = new HashMap<String,Object>(map.size());
        Iterator<String> keys = map.keySet().iterator();
        while(keys.hasNext()){
            String key = keys.next();
            Object val = map.get(key);
            if (val instanceof Map) {
                copy.put(key, deepCopy((Map<String,Object>) val));
            } else if (val instanceof List) {
                copy.put(key, deepCopy((List<Object>) val));
            } else {
                copy.put(key, val);
            }
        }
        return copy;
    }

    /**
     * Copy a list
     */
    public static List<Object>deepCopy(List<Object> list){
        if (list == null) {
             return null;
        };
        List<Object> copy = new ArrayList<Object>(list.size());
        for (int i=0; i<list.size(); i++) {
            Object val = list.get(i);
            if (val instanceof Map) {
                copy.add(deepCopy((Map<String,Object>) val));
            } else if (val instanceof List) {
                copy.add(deepCopy((List<Object>) val));
            } else {
                copy.add(val);
            }
        }
        return copy;
    }

    /**
     * Naive JSONObject copying
     */
    public static JSONObject deepCopy(JSONObject object){
        try {
            return new JSONObject(object.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /**
     * Copy an object if it is a list or map
     */
    public static Object deepCopy(Object object){
        if (object instanceof Map) {
            return deepCopy((Map<String,Object>)object);
        } else if (object instanceof List){
            return deepCopy((List<Object>)object);
        } else if (object instanceof JSONObject) {
            return deepCopy((JSONObject) object);
        } else {
            return object;
        }
    }

    /**
     * Transform a JSONArray to a List<Object>
     */
    public static List<Object> convertJSONArrayToList(JSONArray json){
        List<Object> list = new ArrayList<Object>(json.length());
        for (int i=0; i<json.length(); i++) {
            try {
                list.add(json.get(i));
            } catch (JSONException e) {
                android.util.Log.e(TAG, String.format("Failed to convert JSON: %s", e.getMessage()), e);
            }

        }
        return list;
    }

}
