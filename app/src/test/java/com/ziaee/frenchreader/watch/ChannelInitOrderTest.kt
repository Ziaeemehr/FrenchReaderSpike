package com.ziaee.frenchreader.watch

import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelInitOrderTest {
    // Touches the picker's channel list before anything else loads the resources file (crashed on device).
    @Test fun pickerChannelsLoadWithoutResourcesInitializedFirst() {
        assertTrue(videoChannels.size > 100)
        assertTrue(com.ziaee.frenchreader.resources.DEFAULT_RESOURCES.isNotEmpty())
    }
}
