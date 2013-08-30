package com.simperium.testapp;

import junit.framework.TestCase;

import com.simperium.client.BucketSchema;
import com.simperium.client.BucketSchema.Index;

import com.simperium.testapp.models.Note;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class BucketSchemaTest extends TestCase {

    BucketSchema<Note> noteSchema;

    protected void setUp() throws Exception {
        super.setUp();
        noteSchema = new Note.Schema();
    }

    public void testBuildObject(){
        Map<String,Object> properties = makeProperties("Hola Mundo", "Lorem ipsum.\nLorem ipsum.\nLorem ipsum.\nThe end.");
        Note note = noteSchema.build("test", properties);

        assertEquals(properties.get("title"), note.getTitle());
        assertEquals("Lorem ipsum. Lorem ipsum. Lorem ipsum.", note.getPreview().toString());
    }

    public void testUpdateObject(){
        Map<String,Object> properties = makeProperties("Hola Mundo", "Greetings.");
        Note note = noteSchema.build("test", properties);
        properties = makeProperties("Hello World", null);
        noteSchema.update(note, properties);

        assertNull(note.getContent());
        assertEquals(properties.get("title"), note.getTitle());
    }

    public void testIndexes(){
        Note note = noteSchema.build("test", makeProperties("Hola Mundo", "First line\nSecond line\nThird line\nThat's it."));
        List<Index> indexes = noteSchema.indexesFor(note);
        ArrayList<String> indexNames = new ArrayList<String>();
        indexNames.add("title");
        indexNames.add("content");
        indexNames.add("preview");
        for ( Index index : indexes ) {
            indexNames.remove(index.getName());
        }
        assertEquals(new ArrayList<String>(), indexNames);

    }

    protected static Map<String,Object> makeProperties(){
        return new HashMap<String,Object>();
    }

    protected static Map<String,Object> makeProperties(String title, String content){
        Map<String,Object> properties = new HashMap<String,Object>(2);
        properties.put("title", title);
        if (content != null) {
            properties.put("content", content);
        }
        return properties;
    }


}