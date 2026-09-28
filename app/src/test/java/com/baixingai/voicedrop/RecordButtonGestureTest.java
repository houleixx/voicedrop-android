package com.baixingai.voicedrop;

import com.baixingai.voicedrop.ui.RecordButtonGesture;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordButtonGestureTest {
    @Test public void quickTapLaunchesExactlyOnce() {
        RecordButtonGesture gesture = new RecordButtonGesture();
        gesture.begin(10, 20);
        assertTrue(gesture.release());
        assertFalse(gesture.hold());
        assertFalse(gesture.release());
    }

    @Test public void holdLaunchesBeforeReleaseAndReleaseDoesNotLaunchAgain() {
        RecordButtonGesture gesture = new RecordButtonGesture();
        assertEquals(400, RecordButtonGesture.HOLD_MILLIS);
        gesture.begin(10, 20);
        assertTrue(gesture.hold());
        assertFalse(gesture.hold());
        assertFalse(gesture.release());
    }

    @Test public void canceledPressCannotLaunchButNextPressCan() {
        RecordButtonGesture gesture = new RecordButtonGesture();
        gesture.begin(0, 0);
        gesture.cancel();
        assertFalse(gesture.hold());
        assertFalse(gesture.release());
        gesture.begin(0, 0);
        assertTrue(gesture.release());
    }

    @Test public void movingBeyondToleranceCancelsEvenWhenFingerReturns() {
        RecordButtonGesture gesture = new RecordButtonGesture();
        gesture.begin(10, 20);
        assertFalse(gesture.move(34, 20, 24));
        assertTrue(gesture.move(34, 21, 24));
        assertFalse(gesture.move(10, 20, 24));
        assertFalse(gesture.hold());
        assertFalse(gesture.release());
    }
}
