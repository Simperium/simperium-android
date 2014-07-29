package com.simperium;

import junit.framework.TestCase;

import java.nio.charset.Charset;

public class StringTest extends TestCase {

    public static final String UTF_8 = "UTF-8";

    public void testStringLength()
    throws Exception {

        String string = "Poo? 💩";
        byte[] bytes = string.getBytes();
        String utf8 = new String(string.getBytes(), UTF_8);

        assertEquals(7, bytes.length);

    }
    
}