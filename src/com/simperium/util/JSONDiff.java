package com.simperium.util;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Iterator;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.Patch;

public class JSONDiff {

	public static final String DIFF_VALUE_KEY     = "v";
	public static final String DIFF_OPERATION_KEY = "o";

	public static final String OPERATION_OBJECT  = "O";
	public static final String OPERATION_LIST    = "L";
	public static final String OPERATION_INSERT  = "+";
	public static final String OPERATION_REMOVE  = "-";
	public static final String OPERATION_REPLACE = "r";
	public static final String OPERATION_DIFF    = "d";

	private diff_match_patch dmp;

	public JSONDiff(){
		dmp = new diff_match_patch();
	}

	public Map<String,Object> diff(List<Object> a, List<Object> b){
		HashMap<String,Object> list_diff = new HashMap<String,Object>();
		list_diff.put(DIFF_OPERATION_KEY, OPERATION_LIST);
		HashMap<String,Object> diffs = new HashMap<String,Object>();
		list_diff.put(DIFF_VALUE_KEY, diffs);

		int size_a = a.size();
		int size_b = b.size();

		int prefix_length = commonPrefix(a, b);
        // remove the prefixes
        a = a.subList(prefix_length, size_a);
        b = b.subList(prefix_length, size_b);
        // recalculate the sizes
        size_a = a.size();
        size_b = b.size();


		int suffix_length = commonSuffix(a, b);

        
		List<Object> a_trimmed = a.subList(0, size_a-suffix_length);
		List<Object> b_trimmed = b.subList(0, size_b-suffix_length);

		size_a = a_trimmed.size();
		size_b = b_trimmed.size();

		int max = Math.max(size_a, size_b);

		for (int i=0; i<max; i++) {
			String index = new Integer(i+prefix_length).toString();
			if(i<size_a && i<size_b){
				// both lists have index
				// if values aren't equal add to diff
				if (!a_trimmed.get(i).equals(b_trimmed.get(i))) {
					diffs.put(index, diff(a_trimmed.get(i), b_trimmed.get(i)));
				}
			} else if(i<size_a){
					// b doesn't have it remove from a
					Map<String,Object> diff = new HashMap<String,Object>();
					diff.put(DIFF_OPERATION_KEY, OPERATION_REMOVE);
					diffs.put(index, diff);
			} else if(i<size_b){
					// a doesn't have, b does so add it
					Map<String,Object> diff = new HashMap<String,Object>();
					diff.put(DIFF_OPERATION_KEY, OPERATION_INSERT);
					diff.put(DIFF_VALUE_KEY, b_trimmed.get(i));
					diffs.put(index, diff);
			}
		}

		return list_diff;
	}

	public Map<String,Object> diff(Map<String,Object> a, Map<String,Object> b){
		HashMap<String,Object> diffs = new HashMap<String,Object>();
		if(a == null || b == null){
			return diffs;
		}

		for(String key : a.keySet()){
			if(b.containsKey(key)){
				if (!a.get(key).equals(b.get(key))) {
					diffs.put(key, diff(a.get(key), b.get(key)));
				}
			} else {
				HashMap<String,Object> remove = new HashMap<String,Object>();
				remove.put(DIFF_OPERATION_KEY, OPERATION_REMOVE);
				diffs.put(key, remove);
			}
		}

		for(String key : b.keySet()){
			if (!a.containsKey(key)) {
				HashMap<String,Object> add = new HashMap<String,Object>();
				add.put(DIFF_OPERATION_KEY, OPERATION_INSERT);
				add.put(DIFF_VALUE_KEY, b.get(key));
				diffs.put(key, add);
			}
		}
		HashMap<String,Object> diff = new HashMap<String,Object>();
		if (!diffs.isEmpty()) {
			diff.put(DIFF_OPERATION_KEY, OPERATION_OBJECT);
			diff.put(DIFF_VALUE_KEY, diffs);
		}
		return diff;
	}

	public Map<String,Object> diff(Object a, Object b){
		HashMap<String,Object> m = new HashMap<String,Object>();
		if (a==null || b==null) {
			
		}
		if (a.equals(b)) {
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

		if (String.class.isInstance(a)){
			// diff match patch
			return diff((String)a, (String)b);
		} else if(Map.class.isInstance(a)){
			Map<String,Object> a_map = (Map<String,Object>)a;
			Map<String,Object> b_map = (Map<String,Object>)b;
			return diff(a_map, b_map);
		} else if(List.class.isInstance(a)){
			List<Object> a_list = (List<Object>)a;
			List<Object> b_list = (List<Object>)b;
			return diff(a_list, b_list);
		} else {
			m.put(DIFF_OPERATION_KEY, OPERATION_REPLACE);
			m.put(DIFF_VALUE_KEY, b);
		}

		return m;
	}

	public Map<String,Object> diff(String origin, String target){
		Map<String,Object> m = new HashMap<String,Object>();
		LinkedList diffs = dmp.diff_main((String)origin, (String)target);
		if(diffs.size() > 2){
			dmp.diff_cleanupEfficiency(diffs);
		}
		if(diffs.size() > 0){
			m.put(DIFF_OPERATION_KEY, OPERATION_DIFF);
			m.put(DIFF_VALUE_KEY, dmp.diff_toDelta(diffs));
		}
		return m;
	}

	public Object apply(Object origin, Map<String,Object> patch){
		String method = (String)patch.get(DIFF_OPERATION_KEY);
		if (method.equals(OPERATION_LIST)) {
			return apply((List<Object>)origin, (Map<String,Object>)patch.get(DIFF_VALUE_KEY));
		} else if(method.equals(OPERATION_OBJECT)){
			return apply((Map<String,Object>)origin, (Map<String,Object>)patch.get(DIFF_VALUE_KEY));
		} else if(method.equals(OPERATION_DIFF)){
			return apply((String)origin, (String)patch.get(DIFF_VALUE_KEY));
		}
		return null;
	}

	// todo, do a deep clone of the target before applying transformations
	public Map<String,Object> apply(Map<String,Object> origin, Map<String,Object> patch){
		Map<String,Object> transformed = new HashMap<String,Object>(origin);
		for(String key : patch.keySet()){
			Map<String,Object> operation = (Map<String,Object>)patch.get(key);
			String method = (String)operation.get(DIFF_OPERATION_KEY);
			if (method.equals(OPERATION_INSERT) || method.equals(OPERATION_REPLACE)) {
				transformed.put(key, operation.get(DIFF_VALUE_KEY));
			} else if(method.equals(OPERATION_REMOVE)){
				transformed.remove(key);
			} else if(method.equals(OPERATION_OBJECT)){
				Map<String,Object> child = (Map<String,Object>)transformed.get(key);
				transformed.put(key, apply(child, (Map<String,Object>)operation.get(DIFF_VALUE_KEY)));
			} else if(method.equals(OPERATION_LIST)) {
				List<Object> child = (List<Object>)transformed.get(key);
				transformed.put(key, apply(child, (Map<String,Object>)operation.get(DIFF_VALUE_KEY)));
			} else if(method.equals(OPERATION_DIFF)){
				String child = (String)transformed.get(key);
				transformed.put(key, apply(child, (String)operation.get(DIFF_VALUE_KEY)));
			}
		}
		return transformed;
	}

	public String apply(String origin, String patch){
		LinkedList<Diff> diffs = dmp.diff_fromDelta(origin, patch);
		LinkedList<Patch> patches = dmp.patch_make(origin, diffs);
		Object[] result = dmp.patch_apply(patches, origin);
		return (String)result[0];
	}

	public List<Object> apply(List<Object> origin, Map<String,Object> patch){
		List<Object> transformed = new ArrayList<Object>(origin);
		List<Integer> indexes = new ArrayList<Integer>();
		List<Integer> deleted = new ArrayList<Integer>();
		// iterate the keys on the patch
		for(String key : patch.keySet()){
			indexes.add(new Integer(key));
		}
		Collections.sort(indexes);
		for(Integer index : indexes){
			Map<String,Object> operation = (Map<String,Object>)patch.get(index.toString());
			String method = (String)operation.get(DIFF_OPERATION_KEY);
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
				List<Object> list = (List<Object>)transformed.get(shifted_index);
				transformed.set(shifted_index, apply(list, (Map<String,Object>)operation.get(DIFF_VALUE_KEY)));
			} else if(method.equals(OPERATION_OBJECT)){
				Map<String,Object> hash = (Map<String,Object>)transformed.get(shifted_index);
				transformed.set(shifted_index, apply(hash, (Map<String,Object>)operation.get(DIFF_VALUE_KEY)));
			} else if(method.equals(OPERATION_DIFF)){
				String str = (String)transformed.get(shifted_index);
				transformed.set(shifted_index, apply(str, (String)operation.get(DIFF_VALUE_KEY)));
			}

		}

		return transformed;
	}

	public int commonPrefix(List<Object> a, List<Object> b){
		int a_length = a.size();
		int b_length = b.size();
		int min_length = Math.min(a_length, b_length);
		int same = 0;
		for (int i=0; i<min_length; i++) {
			if (!a.get(i).equals(b.get(i))){
				break;
			} else {
				same ++;
			}
		}
		return same;
	}

	public int commonSuffix(List<Object> a, List<Object> b){
		int a_length = a.size();
		int b_length = b.size();
		int min_length = Math.min(a_length, b_length);
		if (min_length == 0) return 0;
		for (int i=0; i<min_length; i++) {
			if ( !a.get(a_length-i-1).equals(b.get(b_length-i-1)))
				return i;
		}
		return min_length;
	}

    /**
     * Copy a hash
     */
    public static Map<String, java.lang.Object> deepCopy(Map<String, java.lang.Object> map){
        if (map == null) {
            return null;
        };
        Map<String,java.lang.Object> copy = new HashMap<String,java.lang.Object>(map.size());
        Iterator keys = map.keySet().iterator();
        while(keys.hasNext()){
            String key = (String)keys.next();
            java.lang.Object val = map.get(key);
            // Logger.log(String.format("Hello! %s", json.get(key).getClass().getName()));
            if (val instanceof Map) {
                copy.put(key, deepCopy((Map<String,java.lang.Object>) val));
            } else if (val instanceof List) {
                copy.put(key, deepCopy((List<java.lang.Object>) val));
            } else {
                copy.put(key, val);
            }
        }
        return copy;
    }

    /**
     * Copy a list
     */
    public static List<java.lang.Object>deepCopy(List<java.lang.Object> list){
        if (list == null) {
             return null;
        };
        List<java.lang.Object> copy = new ArrayList<java.lang.Object>(list.size());
        for (int i=0; i<list.size(); i++) {
            java.lang.Object val = list.get(i);
            if (val instanceof Map) {
                copy.add(deepCopy((Map<String,java.lang.Object>) val));
            } else if (val instanceof List) {
                copy.add(deepCopy((List<java.lang.Object>) val));
            } else {
                copy.add(val);
            }
        }
        return copy;
    }


}
