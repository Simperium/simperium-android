package com.simperium.client;

import com.simperium.util.JSONDiff;

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * An interface to allow applications to provide a schema for a bucket and a way
 * instatiate custom BucketObject instances
 */
public abstract class BucketSchema<T extends Syncable> {

    public abstract String getRemoteName();
    public abstract T build(String key, Map<String,Object>properties);
    public abstract void update(T object, Map<String,Object>properties);

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

    public T buildWithDefaults(String key, Map<String,Object> properties) {
        updateDefaultValues(properties);
        return build(key, properties);
    }

    public void updateWithDefaults(T object, Map<String,Object> properties){
        updateDefaultValues(properties);
        update(object, properties);
    }

    protected void setDefault(String key, Object value){
        defaultValues.put(key, value);
    }

    protected void updateDefaultValues(Map<String,Object> properties){
        Set<Entry<String,Object>> defaults = defaultValues.entrySet();
        Iterator<Entry<String,Object>> iterator = defaults.iterator();
        while(iterator.hasNext()){
            Entry<String,Object> entry = iterator.next();
            String key = entry.getKey();
            if (!properties.containsKey(key)) {
                properties.put(key, JSONDiff.deepCopy(entry.getValue()));
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

    private static class AutoIndexer<S extends Syncable> implements Indexer<S> {

        public List<Index> index(S object){
            Set<Entry<String,Object>> entries = object.getDiffableValue().entrySet();
            List<Index> indexes = new ArrayList<Index>(entries.size());
            Iterator<Entry<String,Object>> iterator = entries.iterator();
            while(iterator.hasNext()){
                Entry<String,Object> entry = iterator.next();
                addIndex(indexes, entry.getKey(), entry.getValue());
            }
            return indexes;
        }

        private void addIndex(List<Index> indexes, String key, Object value){
            if (value instanceof List) {
                List list = (List) value;
                Iterator<Object> values = list.iterator();
                while(values.hasNext()){
                    addIndex(indexes, key, values.next());
                }
            } else {
                indexes.add(new Index(key, value));
            }
        }
    }

}
