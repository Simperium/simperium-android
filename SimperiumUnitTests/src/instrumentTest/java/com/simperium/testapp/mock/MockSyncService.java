package com.simperium.testapp.mock;

import com.simperium.client.SyncService;

public class MockSyncService {

    /**
     * Just runs the on the same thread.
     */
    public static SyncService service(){
        return new SyncService(){
            @Override
            public void submit(Runnable runnable){
                runnable.run();
            }
        };
    }

}