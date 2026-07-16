package com.baixingai.voicedrop.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CommandStateStoreTest {
    @Test public void controlsRoundTripForReconnectDelivery() {
        List<CommandStateStore.Control> decoded = CommandStateStore.decodeControls(
                CommandStateStore.encodeControls(Arrays.asList(
                        new CommandStateStore.Control("confirm", "cmd-1"),
                        new CommandStateStore.Control("cancel", "cmd-2"))));

        assertEquals(2, decoded.size());
        assertEquals("confirm", decoded.get(0).type);
        assertEquals("cmd-1", decoded.get(0).id);
        assertEquals("cancel", decoded.get(1).type);
        assertEquals("cmd-2", decoded.get(1).id);
    }

    @Test public void confirmationsRoundTripWithServerSummary() {
        List<CommandStateStore.Confirmation> decoded = CommandStateStore.decodeConfirmations(
                CommandStateStore.encodeConfirmations(Arrays.asList(
                        new CommandStateStore.Confirmation("cmd-3", "要删掉《文章2》吗？"))));

        assertEquals(1, decoded.size());
        assertEquals("cmd-3", decoded.get(0).id);
        assertEquals("要删掉《文章2》吗？", decoded.get(0).text);
    }

    @Test public void malformedStateIsIgnored() {
        assertEquals(0, CommandStateStore.decodeControls("not-json").size());
        assertEquals(0, CommandStateStore.decodeConfirmations("[{\"id\":\"\"}]").size());
    }
}
