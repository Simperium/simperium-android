package com.simperium.util;

import java.util.UUID;

public class Uuid {

    public static String uuid(){
        return UUID.randomUUID().toString().replace("-","");
    }

    /**
     * Take only the specified number of characters from the front of the UUID 
     */
    public static String uuid(int length){
        return uuid().substring(0, length);
    }

}

