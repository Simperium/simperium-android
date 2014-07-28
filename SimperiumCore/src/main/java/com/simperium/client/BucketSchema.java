package com.simperium.client;

import com.simperium.util.JSONDiff;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * An interface to allow applications to provide a schema for a bucket and a way
 * instatiate custom BucketObject instances
 */
public abstract class BucketSchema<T extends Syncable> {

    static public final String TAG = "Simperium";

    public abstract String getRemoteName();
    public abstract T build(String key, JSONObject properties);
    public abstract void update(T object, JSONObject properties);

    public interface Indexer<S extends Syncable> {
        public List<Index> index(S object);
    }

    public static class Index {
        private String name;
        private Object value;

        public Index(String name, Object value){
            this.name = name;
            this.value = value;
        }

        public String getName(){
            return name;
        }

        public Object getValue(){
            return value;
        }

        public String toString(){
            return String.format("%s : %s", name, value);
        }
    }

    private List<Indexer<T>> indexers = Collections.synchronizedList(new ArrayList<Indexer<T>>());
    private Map<String,Object> defaultValues = new HashMap<String,Object>();
    private FullTextIndex mFullTextIndex;

    public T buildWithDefaults(String key, JSONObject properties) {
        updateDefaultValues(properties);
        return build(key, properties);
    }

    public void updateWithDefaults(T object, JSONObject properties){
        updateDefaultValues(properties);
        update(object, properties);
    }

    protected void setDefault(String key, Object value){
        defaultValues.put(key, value);
    }

    protected void updateDefaultValues(JSONObject properties){
        Set<Entry<String,Object>> defaults = defaultValues.entrySet();
        Iterator<Entry<String,Object>> iterator = defaults.iterator();
        while(iterator.hasNext()){
            Entry<String,Object> entry = iterator.next();
            String key = entry.getKey();
            if (!properties.has(key)) {
                try {
                    properties.put(key, JSONDiff.deepCopy(entry.getValue()));
                } catch (JSONException e) {
                    // unable to set default value
                }
            }
        }
    }

    public List<Index> indexesFor(T object){
        // run all of the indexers
        List<Index> indexes = new ArrayList<Index>();
        Iterator<Indexer<T>> indexerIterator = indexers.iterator();
        while(indexerIterator.hasNext()){
            indexes.addAll(indexerIterator.next().index(object));
        }
        return indexes;
    }

    public void addIndex(Indexer indexer){
        indexers.add(indexer);
    }

    public void removeIndex(Indexer indexer){
        indexers.remove(indexer);
    }

    public void autoIndex(){
        indexers.add(0, new AutoIndexer<T>());
    }

    public FullTextIndex<T> setupFullTextIndex(String ... indexNames){

        FullTextIndex.Indexer<T> indexer = new FullTextIndex.Indexer<T>() {

            @Override
            public Map<String,String> index(String[] keys, T object){
                Map<String,String> indexValues = new HashMap<String,String>(keys.length);
                JSONObject values = object.getDiffableValue();
                for (String key : keys) {
                    Object value = values.opt(key);
                    if (value != null) {
                        indexValues.put(key, value.toString());
                    }
                }

                return indexValues;
            }

        };

        return setupFullTextIndex(indexer, indexNames);
    }

    public FullTextIndex<T> setupFullTextIndex(FullTextIndex.Indexer<T> indexer, String ... indexNames){
        mFullTextIndex = new FullTextIndex<T>(indexer, indexNames);
        return mFullTextIndex;
    }

    public boolean hasFullTextIndex(){
        return mFullTextIndex != null;
    }

    public FullTextIndex<T> getFullTextIndex(){
        return mFullTextIndex;
    }

    private static class AutoIndexer<S extends Syncable> implements Indexer<S> {

        public List<Index> index(S object){

            JSONObject values = object.getDiffableValue();
            List<Index> indexes = new ArrayList<Index>(values.length());
            Iterator keys = values.keys();

            while (keys.hasNext()) {
                String key = keys.next().toString();
                try {
                    if (values.has(key))
                        addIndex(indexes, key, values.get(key));
                } catch (JSONException e) {
                    // failed to index key
                }
            }

            return indexes;
        }

        private void addIndex(List<Index> indexes, String key, Object value){
            if (value instanceof JSONArray) {
                JSONArray list = (JSONArray) value;
                int length = list.length();
                for (int i = 0; i < length; i++) {
                    try {
                        addIndex(indexes, key, list.get(i));
                    } catch (JSONException e) {
                        // couldn't get array value
                    }
                }
            } else {
                indexes.add(new Index(key, value));
            }
        }

    }

}
