package com.simperium.android;

import com.simperium.client.Channel;
import com.simperium.client.Channel.Serializer;
import com.simperium.client.Channel.SerializedQueue;
import com.simperium.client.Change;
import com.simperium.client.RemoteChange;
import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.Syncable;
import com.simperium.util.Logger;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.Context;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class FileQueueSerializer implements Serializer {

    public static final String TAG="Simperium.FileQueueSerializer";

    public static final String PENDING_KEY = "pending";
    public static final String QUEUED_KEY = "queued";

    private Context mContext;
    
    public FileQueueSerializer(Context context){
        mContext = context;
    }

    @Override
    public <T extends Syncable> SerializedQueue<T> restore(Bucket<T> bucket){
        try {
            return restoreFromFile(bucket);
        } catch (java.io.IOException e) {
            Logger.log(TAG, "Unable to restore queue", e);
            return new SerializedQueue<T>();
        } catch (org.json.JSONException e) {
            Logger.log(TAG, "Unable to restore queue", e);
            return new SerializedQueue<T>();
        }
    }

    @Override
    public <T extends Syncable> void reset(Bucket<T> bucket){
        
    }
    /**
     * Save state of pending and locally queued items
     */
    private <T extends Syncable> void saveToFile(Bucket<T> bucket, SerializedQueue<T> data) throws java.io.IOException {
        //  construct JSON string of pending and local queue
        String fileName = getFileName(bucket);
        Logger.log(TAG, String.format("Saving to file %s", fileName));
        FileOutputStream stream = null;
        try {
            stream = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
            Map<String,Object> serialized = new HashMap<String,Object>(2);
            serialized.put(PENDING_KEY, data.pending);
            serialized.put(QUEUED_KEY, data.queued);
            JSONObject json = Channel.serializeJSON(serialized);
            String jsonString = json.toString();
            Logger.log(TAG, String.format("Saving: %s", jsonString));
            stream.write(jsonString.getBytes(), 0, jsonString.length());
        } finally {
            if(stream !=null) stream.close();
        }
    }
    
    /**
     * 
     */
    private <T extends Syncable> SerializedQueue<T> restoreFromFile(Bucket<T> bucket) throws java.io.IOException, org.json.JSONException {
        BufferedInputStream stream = null;
        List<Change<T>> queued = new ArrayList<Change<T>>();
        Map<String,Change<T>> pending = new HashMap<String,Change<T>>();
        try {
            stream = new BufferedInputStream(mContext.openFileInput(getFileName(bucket)));
            byte[] contents = new byte[1024];
            int bytesRead = 0;
            StringBuilder builder = new StringBuilder();
            while((bytesRead = stream.read(contents)) != -1){
                builder.append(new String(contents, 0, bytesRead));
            }
            JSONObject json = new JSONObject(builder.toString());
            Map<String,Object> changeData = Channel.convertJSON(json);
            Logger.log(TAG, String.format("We have changes from serialized file %s", changeData));
            
            if (changeData.containsKey(PENDING_KEY)) {
                Map<String,Map<String,Object>> pendingData = (Map<String,Map<String,Object>>)changeData.get(PENDING_KEY);
                Iterator<Map.Entry<String,Map<String,Object>>> pendingEntries = pendingData.entrySet().iterator();
                while(pendingEntries.hasNext()){
                    Map.Entry<String, Map<String,Object>> entry = pendingEntries.next();
                    try {
                        T object = bucket.get(entry.getKey());                                
                        Change<T> change = Change.buildChange(object, entry.getValue());
                        pending.put(entry.getKey(), change);
                    } catch (BucketObjectMissingException e) {
                        Logger.log(TAG, String.format("Missing local object for change %s", entry.getKey()));
                    }
                }
            }
            if (changeData.containsKey(QUEUED_KEY)) {
                List<Map<String,Object>> queuedData = (List<Map<String,Object>>)changeData.get(QUEUED_KEY);
                Iterator<Map<String,Object>> queuedItems = queuedData.iterator();
                while(queuedItems.hasNext()){
                    Map<String,Object> queuedItem = queuedItems.next();
                    String key = (String) queuedItem.get(Change.ID_KEY);
                    try {
                        T object = bucket.get(key);
                        queued.add(Change.buildChange(object, queuedItem));                                
                    } catch (BucketObjectMissingException e) {
                        Logger.log(TAG, String.format("Missing local object for change %s", key));
                    }
                }
            }
            
        } finally {
            if(stream != null) stream.close();
        }
        return new SerializedQueue(pending, queued);
    }
    /**
     * 
     */
    private void clearFile(Bucket bucket){
        mContext.deleteFile(getFileName(bucket));
    }

    private String getFileName(Bucket bucket){
        return String.format("simperium-queue-%s-%s.json", bucket.getRemoteName(), bucket.getUser().getAccessToken());
    }

}