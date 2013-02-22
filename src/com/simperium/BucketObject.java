package com.simperium.client;


public abstract class BucketObject implements Bucket.Diffable {
    
    private Bucket bucket;
    private String simperiumId;
    private Integer version = 0;
    
    public Bucket getBucket(){
        return bucket;
    }
    
    public void setBucket(Bucket bucket){
        this.bucket = bucket;
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
    
    public Boolean isNew(){
        return version == null || version == 0;
    }
    
    public String bucketName(){
        Bucket bucket = getBucket();
        if (bucket != null) {
            return bucket.getName();
        }
        return null;
    }
}