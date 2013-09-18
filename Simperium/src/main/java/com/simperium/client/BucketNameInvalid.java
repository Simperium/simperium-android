package com.simperium.client;

import com.simperium.SimperiumException;

public class BucketNameInvalid extends SimperiumException {

    public final String name;

    public BucketNameInvalid(String name){
        super(String.format("`%s` is an invalid bucket name", name));
        this.name = name;
    }

}