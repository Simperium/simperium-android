package com.simperium.util;

import com.simperium.client.SyncService;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class BasicSyncService implements SyncService {

    private ExecutorService service = Executors.newFixedThreadPool(1);

    @Override
    public void submit(Runnable runnable){
        service.submit(runnable);
    }


}