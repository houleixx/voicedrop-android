package com.baixingai.voicedrop;

import com.baixingai.voicedrop.ui.HoldToTalkGesture;
import com.baixingai.voicedrop.ui.HoldToTalkTranscript;

import org.junit.Assert;
import org.junit.Test;

public class HoldToTalkGestureTest {
    @Test public void upwardDragPastThresholdCancels() {
        Assert.assertFalse(HoldToTalkGesture.shouldCancel(500f, 461f, 40f));
        Assert.assertTrue(HoldToTalkGesture.shouldCancel(500f, 460f, 40f));
        Assert.assertFalse(HoldToTalkGesture.shouldCancel(500f, 540f, 40f));
    }

    @Test public void systemCancelWithoutUpwardDragDoesNotAbort() {
        Assert.assertFalse(HoldToTalkGesture.shouldAbortOnEnd(true, false));
        Assert.assertFalse(HoldToTalkGesture.shouldAbortOnEnd(false, false));
        Assert.assertTrue(HoldToTalkGesture.shouldAbortOnEnd(true, true));
        Assert.assertTrue(HoldToTalkGesture.shouldAbortOnEnd(false, true));
    }

    @Test public void finalTextArrivingAfterReleaseBecomesSubmittedText() {
        HoldToTalkTranscript transcript = new HoldToTalkTranscript();

        transcript.accept("改一下标题", false);
        Assert.assertEquals("改一下标题", transcript.bestText());

        transcript.accept("把标题改得更温柔", true);
        Assert.assertEquals("把标题改得更温柔", transcript.bestText());
    }

    @Test public void multipleFinalSegmentsAreJoinedWithSpaces() {
        HoldToTalkTranscript transcript = new HoldToTalkTranscript();

        transcript.accept("第一句", true);
        transcript.accept("第二句", true);

        Assert.assertEquals("第一句 第二句", transcript.bestText());
    }

    @Test public void bubbleTextShowsListeningHintUntilRecognitionArrives() {
        HoldToTalkTranscript transcript = new HoldToTalkTranscript();

        Assert.assertEquals("在听…", transcript.bubbleText());

        transcript.accept("把标题改短", false);
        Assert.assertEquals("把标题改短", transcript.bubbleText());

        transcript.accept("把标题改得更温柔", true);
        Assert.assertEquals("把标题改得更温柔", transcript.bubbleText());
    }
}
