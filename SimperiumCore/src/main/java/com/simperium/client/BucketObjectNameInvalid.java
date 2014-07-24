package com.simperium.client;

import com.simperium.SimperiumException;

public class BucketObjectNameInvalid extends SimperiumException {

    public final String name;

    public BucketObjectNameInvalid(String name){
        super(String.format("`%s` is not a valid object name", name));
        this.name = name;
    }

}