package com.simperium.models;

import com.simperium.client.BucketObject;
import com.simperium.client.BucketSchema;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Note extends BucketObject {
    
    public static class Schema extends BucketSchema<Note> {
        public static final String BUCKET_NAME="notes";

        public Schema(){
            autoIndex();
            addIndex(contentIndexer);
            addIndex(nullIndexer);
            setupFullTextIndex("tags", "content");
            setDefault("tags", new JSONArray());
            setDefault("deleted", false);
        }

        private Indexer<Note> contentIndexer = new Indexer<Note>(){
            @Override
            public List<Index> index(Note note){
                List<Index> indexes = new ArrayList<Index>(1);
                indexes.add(new Index("preview", note.getPreview()));
                return indexes;
            }
        };

        private Indexer<Note> nullIndexer = new Indexer<Note>() {
            @Override
            public List<Index> index(Note note) {
                List<Index> indexes = new ArrayList<Index>(1);
                indexes.add(new Index("null_column", null));
                return indexes;
            }
        };

        @Override
        public String getRemoteName(){
            return BUCKET_NAME;
        }

        @Override
        public Note build(String key, JSONObject properties){
            return new Note(key, properties);
        }

        @Override
        public void update(Note note, JSONObject properties){
            note.setProperties(properties);
        }
    }

    private static final String SPACE=" ";
    private StringBuilder preview;

    public Note(String key, JSONObject properties){
        super(key, properties);
    }

    public void addTag(String tag){
        JSONArray tags = (JSONArray) get("tags");
        tags.put(tag);
    }

    public void addTags(String ... newTags){
        JSONArray tags = (JSONArray) get("tags");
        for(String tag : newTags){
            tags.put(tag);
        }
    }

    public void setTitle(String title){
        put("title", title);
    }

    public String getTitle(){
        return (String) get("title");
    }

    public String getContent(){
        return (String) get("content");
    }

    public void setContent(String content){
        preview = null;
        put("content", content);
    }

    public void put(String key, Object value){
        try {
            getProperties().put(key, value);
        } catch (JSONException e) {
            // Do nothing
        }
    }

    public Object get(String key){
        try {
            return getProperties().get(key);
        } catch (JSONException e) {
            // Do nothing
            return null;
        }
    }

    public CharSequence getPreview(){
        if (preview != null) {
            return preview;
        }
        String content = getContent();
        if (content == null) {
            return "";
        }
        // just the first three lines
        preview = new StringBuilder();
        int start = 0;
        int position = -1;
        int lines = 0;
        do {
            position = content.indexOf("\n", start);
            // if there are no new lines in the whole thing, we'll just take the whole thing
            if (position == -1 && start == 0) {
                position = content.length();
            }
            if (position > start + 1) {
                int length = preview.length();
                if (length > 0) {
                    preview.append(SPACE);
                }
                preview.append(content.subSequence(start, position));
                if (length > 320) {
                    break;
                }
                lines ++;
            }
            start = position + 1;
        } while(lines < 3 && position > -1 && start > 0);
        return preview;
    }
}