package com.simperium;

import com.simperium.Bucket;

public abstract class BucketObject implements Bucket.Diffable {
    
    private Bucket bucket;
    private String simperiumId;
    private Integer version = 0;
    
    public Bucket getBucket(){
        return bucket;
    }
    
    public void setBucket(Bucket bucket){
        this.bucket = bucket;
        bucket.add(this);
    }
    
    public String getSimperiumId(){
        return simperiumId;
    }
    
    public void setSimperiumId(String simperiumId){
        this.simperiumId = simperiumId;
    }
    
    public Integer getVersion(){
        return version;
    }
    
    public void setVersion(Integer version){
        this.version = version;
    }
        
}