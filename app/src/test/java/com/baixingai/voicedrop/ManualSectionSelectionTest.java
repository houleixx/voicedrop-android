package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.ManualSectionSelection;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ManualSectionSelectionTest {
    @Test
    public void tappedDestinationStaysSelectedWhileSmoothScrollPassesIntermediateSections() {
        ManualSectionSelection selection = new ManualSectionSelection();

        selection.onSectionTapped(0);
        assertEquals(4, selection.onSectionTapped(4));
        assertEquals(4, selection.onSectionReported(1));
        assertEquals(4, selection.onSectionReported(2));
        assertEquals(4, selection.onSectionReported(3));
        assertEquals(4, selection.onSectionReported(4));
    }

    @Test
    public void scrollReportsResumeSelectingSectionsAfterTappedDestinationIsReached() {
        ManualSectionSelection selection = new ManualSectionSelection();

        selection.onSectionTapped(4);
        assertEquals(4, selection.onSectionReported(2));
        assertEquals(4, selection.onSectionReported(4));
        assertEquals(3, selection.onSectionReported(3));
    }

    @Test
    public void tappingTheAlreadySelectedSectionDoesNotLockLaterManualScrolling() {
        ManualSectionSelection selection = new ManualSectionSelection();

        selection.onSectionTapped(0);
        assertEquals(1, selection.onSectionReported(1));
    }

    @Test
    public void releasingAnUnreachableTappedTargetRestoresManualScrollTracking() {
        ManualSectionSelection selection = new ManualSectionSelection();

        selection.onSectionTapped(7);
        assertEquals(7, selection.onSectionReported(6));
        selection.releaseTappedSection();
        assertEquals(6, selection.onSectionReported(6));
    }
}
