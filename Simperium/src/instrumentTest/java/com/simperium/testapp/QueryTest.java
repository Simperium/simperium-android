package com.simperium.testapp;

import junit.framework.TestCase;

import com.simperium.client.Query;

public class QueryTest extends TestCase {

    private Query<?> query;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        query = new Query();
    }

    public void testQuery(){
        assertEquals(0, query.getSorters().size());
    }

}