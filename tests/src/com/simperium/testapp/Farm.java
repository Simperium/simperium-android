package com.simperium.testapp;

import com.simperium.client.Bucket;

import java.util.Map;

public class Farm extends Bucket.Object {
    public static String BUCKET_NAME="farm";

    public static class Schema extends Bucket.Schema<Farm> {
        @Override
        public String getRemoteName(){
            return Farm.BUCKET_NAME;
        }
        @Override
        public Farm build(String key, Map<String,Object> properties){
            Farm farm = new Farm(key, properties);
            return farm;
        }
    }

    public Farm(String key, Map<String,Object> properties){
        super(key, properties);
    }

    public Map<String,Object> getProperties(){
        return properties;
    }
}