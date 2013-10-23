package com.simperium;

import junit.framework.TestCase;

import com.simperium.client.BucketSchema;
import com.simperium.client.BucketSchema.Index;

import com.simperium.models.Note;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.json.JSONObject;
import org.json.JSONException;

public class BucketSchemaTest extends TestCase {

    BucketSchema<Note> noteSchema;

    protected void setUp() throws Exception {
        super.setUp();
        noteSchema = new Note.Schema();
    }

    public void testBuildObject()
    throws Exception {
        JSONObject properties = makeProperties("Hola Mundo", "Lorem ipsum.\nLorem ipsum.\nLorem ipsum.\nThe end.");
        Note note = noteSchema.build("test", properties);

        assertEquals(properties.get("title"), note.getTitle());
        assertEquals("Lorem ipsum. Lorem ipsum. Lorem ipsum.", note.getPreview().toString());
    }

    public void testUpdateObject()
    throws Exception {
        JSONObject properties = makeProperties("Hola Mundo", "Greetings.");
        Note note = noteSchema.build("test", properties);
        properties = makeProperties("Hello World", null);
        noteSchema.update(note, properties);

        assertNull(note.getContent());
        assertEquals(properties.get("title"), note.getTitle());
    }

    public void testIndexes()
    throws Exception {
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

    protected static JSONObject makeProperties(){
        return new JSONObject();
    }

    protected static JSONObject makeProperties(String title, String content)
    throws JSONException {
        JSONObject properties = new JSONObject();
        properties.put("title", title);
        if (content != null) {
            properties.put("content", content);
        }
        return properties;
    }


}