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
        assertFalse(PhotoLoadPolicy.shouldPoll(PhotoLoadPolicy.Intent.ORIGINAL));
    }

    @Test
    public void generatedPhotoFailureKeepsPollingUntilTheImageExists() {
        assertEquals(PhotoLoadPolicy.FailureState.GENERATING,
                PhotoLoadPolicy.failureState(PhotoLoadPolicy.Intent.GENERATED));
        assertTrue(PhotoLoadPolicy.shouldPoll(PhotoLoadPolicy.Intent.GENERATED));
    }

    @Test
    public void detailLoadsAtMostThreePhotosConcurrently() {
        assertEquals(3, PhotoLoadPolicy.concurrentLoads());
    }
}
