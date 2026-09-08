package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhotoLoadPolicyTest {
    @Test
    public void ordinaryPhotoFailureIsNotReportedAsGeneration() {
        assertEquals(PhotoLoadPolicy.FailureState.LOAD_FAILED,
                PhotoLoadPolicy.failureState(PhotoLoadPolicy.Intent.ORIGINAL));
        assertTrue(PhotoLoadPolicy.shouldPoll(PhotoLoadPolicy.Intent.ORIGINAL));
        assertEquals(15_000L, PhotoLoadPolicy.timeoutMs(PhotoLoadPolicy.Intent.ORIGINAL));
    }

    @Test
    public void generatedPhotoFailureKeepsPollingUntilTheImageExists() {
        assertEquals(PhotoLoadPolicy.FailureState.GENERATING,
                PhotoLoadPolicy.failureState(PhotoLoadPolicy.Intent.GENERATED));
        assertTrue(PhotoLoadPolicy.shouldPoll(PhotoLoadPolicy.Intent.GENERATED));
    }

    @Test
    public void reopenedGenerationKeepsItsOriginalDeadline() {
        assertEquals(PhotoLoadPolicy.Intent.GENERATED, PhotoLoadPolicy.restoredIntent(true, false));
        assertEquals(1000L, PhotoLoadPolicy.startedAt(1000L, 90000L, false));
        assertEquals(1000L, PhotoLoadPolicy.startedAt(1000L, 400000L, false));
        assertEquals(400000L, PhotoLoadPolicy.startedAt(1000L, 400000L, true));
    }

    @Test
    public void missingProvenanceStillRetriesWithoutClaimingGeneration() {
        assertEquals(PhotoLoadPolicy.Intent.UNKNOWN, PhotoLoadPolicy.restoredIntent(false, false));
        assertTrue(PhotoLoadPolicy.shouldPoll(PhotoLoadPolicy.Intent.UNKNOWN));
        assertEquals(300_000L, PhotoLoadPolicy.timeoutMs(PhotoLoadPolicy.Intent.UNKNOWN));
        assertEquals(PhotoLoadPolicy.Intent.ORIGINAL, PhotoLoadPolicy.restoredIntent(true, true));
    }

    @Test
    public void detailLoadsAtMostThreePhotosConcurrently() {
        assertEquals(3, PhotoLoadPolicy.concurrentLoads());
    }
}
