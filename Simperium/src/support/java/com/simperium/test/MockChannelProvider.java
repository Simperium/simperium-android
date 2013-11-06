package com.simperium.test;

import com.simperium.client.Bucket;
import com.simperium.client.ChannelProvider;

import java.util.ArrayList;
import java.util.List;

public class MockChannelProvider implements ChannelProvider {

    public List<String> logs = new ArrayList<String>();

    @Override
    public MockChannel buildChannel(Bucket bucket) {
        return new MockChannel(bucket);
    }

    @Override
    public void log(int level, CharSequence message) {
        // noop
        logs.add(message.toString());
    }

    @Override
    public int getLogLevel() {
        return ChannelProvider.LOG_DISABLED;
    }

    public String getLastLog() {
        if (logs.size() > 0) {
            return logs.get(logs.size()-1);
        }
        return null;
    }

}
