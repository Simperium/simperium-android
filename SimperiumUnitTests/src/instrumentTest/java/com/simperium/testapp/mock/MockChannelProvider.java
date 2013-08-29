package com.simperium.testapp.mock;

import com.simperium.client.Bucket;
import com.simperium.client.ChannelProvider;

public class MockChannelProvider implements ChannelProvider {

    public MockChannel buildChannel(Bucket bucket){
        return new MockChannel(bucket);
    }

}
