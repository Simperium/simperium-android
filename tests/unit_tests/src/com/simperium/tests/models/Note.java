package com.simperium.tests.models;

import com.simperium.client.BucketObject;
import com.simperium.client.BucketSchema;

import com.simperium.tests.models.Note;

import java.util.Map;

public class Note extends BucketObject {
    
    public static class Schema implements BucketSchema<Note> {
        public static final String BUCKET_NAME="notes";

        @Override
        public String getRemoteName(){
            return BUCKET_NAME;
        }

        @Override
        public Note build(String key, Map<String,Object> properties){
            return new Note(key, properties);
        }
    }

    public Note(String key, Map<String,Object> properties){
        super(key, properties);
    }

    public void setTitle(String title){
        put("title", title);
    }

    public void put(String key, Object value){
        getProperties().put(key, value);
    }

    public Object get(String key){
        return getProperties().get(key);
    }

    public Map<String,Object> getProperties(){
        return getDiffableValue();
    }
}