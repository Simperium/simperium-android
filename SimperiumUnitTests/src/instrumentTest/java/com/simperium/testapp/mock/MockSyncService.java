package com.simperium.testapp.mock;

import com.simperium.client.SyncService;

public class MockSyncService implements SyncService {

    /**
     * Just runs the on the same thread.
     */
    @Override
    public void submit(Runnable runnable){
        runnable.run();
    }

    public static SyncService service(){
        return new MockSyncService();
    }

}