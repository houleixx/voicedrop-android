package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.XhsExportPolicy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XhsExportPolicyTest {
    @Test
    public void missingPhotosAreFilledWithTextCardsUpToNineSlots() {
        assertEquals(9, XhsExportPolicy.generatedCardSlots(0));
        assertEquals(6, XhsExportPolicy.generatedCardSlots(3));
        assertEquals(0, XhsExportPolicy.generatedCardSlots(9));
        assertEquals(0, XhsExportPolicy.generatedCardSlots(99));
    }
}
