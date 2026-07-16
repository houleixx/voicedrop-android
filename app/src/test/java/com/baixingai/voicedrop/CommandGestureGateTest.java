package com.baixingai.voicedrop;

import com.baixingai.voicedrop.ui.CommandGestureGate;

import org.junit.Test;

import static org.junit.Assert.*;

public class CommandGestureGateTest {
    @Test public void quickTapBlocksReentryUntilMicrophoneHandoffCompletes() {
        CommandGestureGate gate = new CommandGestureGate();

        assertTrue(gate.beginPress());
        assertTrue(gate.beginHandoff());
        assertFalse(gate.beginPress());

        gate.complete();
        assertTrue(gate.beginPress());
    }

    @Test public void finishingCommandBlocksAnotherGestureUntilFinalResultCompletes() {
        CommandGestureGate gate = new CommandGestureGate();

        assertTrue(gate.beginPress());
        assertTrue(gate.confirmLongPress());
        assertTrue(gate.beginFinish());
        assertFalse(gate.beginPress());

        gate.complete();
        assertTrue(gate.beginPress());
    }

    @Test public void cancelReturnsGateToIdle() {
        CommandGestureGate gate = new CommandGestureGate();

        assertTrue(gate.beginPress());
        gate.cancel();

        assertTrue(gate.beginPress());
    }

    @Test public void pauseCancelsHandoffButKeepsFinishingCommandExclusive() {
        CommandGestureGate handoff = new CommandGestureGate();
        assertTrue(handoff.beginPress());
        assertTrue(handoff.beginHandoff());
        handoff.cancelPendingPress();
        assertTrue(handoff.beginPress());

        CommandGestureGate finishing = new CommandGestureGate();
        assertTrue(finishing.beginPress());
        assertTrue(finishing.confirmLongPress());
        assertTrue(finishing.beginFinish());
        finishing.cancelPendingPress();
        assertFalse(finishing.beginPress());
        finishing.complete();
        assertTrue(finishing.beginPress());
    }
}
