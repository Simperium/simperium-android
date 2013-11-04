package com.simperium.test;

import com.simperium.client.Bucket;
import com.simperium.client.ChannelProvider;

public class MockChannelProvider implements ChannelProvider {

    @Override
    public MockChannel buildChannel(Bucket bucket) {
        return new MockChannel(bucket);
    }

    @Override
    public void log(int level, CharSequence message) {
        // noop
    }

    @Override
    public int getLogLevel() {
        return ChannelProvider.LOG_DISABLED;
    }

}
