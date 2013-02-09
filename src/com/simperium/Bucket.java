package com.simperium;

import com.simperium.User;

public class Bucket {
    
    private String name;
    private User user;
    
    public Bucket(String name, User user){
        this.name = name;
        this.user = user;
    }
    
    public String getName(){
        return name;
    }
    
    // starts tracking the object
    public void add(Diffable object){
        if (!object.bucket.equals(this)) {
            object.setBucket(object);
        }
    }
    
    // updates the object
    public void update(Diffable object){
    }
    
    public interface Diffable {
        void setBucket(Bucket bucket);
        Bucket getBucket();
        void setSimperiumId(String id);
        String getSimperiumId();
    }
    
}