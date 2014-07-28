package com.simperium;

import com.simperium.client.Query;

import junit.framework.TestCase;

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