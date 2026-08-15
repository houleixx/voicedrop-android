package com.baixingai.voicedrop.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WritingStylePresentationTest {
    @Test public void unchangedCurrentVersionIsAlreadyDefault() {
        assertEquals(WritingStylePresentation.Action.CURRENT_DEFAULT,
                WritingStylePresentation.action(true, true, "短句", " 短句 "));
    }

    @Test public void unchangedHistoricalVersionCanBecomeDefault() {
        assertEquals(WritingStylePresentation.Action.SET_DEFAULT,
                WritingStylePresentation.action(true, false, "短句", "短句"));
    }

    @Test public void editsAlwaysCreateANewDefaultVersion() {
        assertEquals(WritingStylePresentation.Action.SAVE_AS_NEW_DEFAULT,
                WritingStylePresentation.action(true, false, "短句", "短句，保留细节"));
        assertEquals(WritingStylePresentation.Action.SAVE_AS_NEW_DEFAULT,
                WritingStylePresentation.action(false, false, "", "第一份风格"));
    }

    @Test public void labelsDescribeBackendSemantics() {
        assertEquals("当前默认", WritingStylePresentation.actionLabel(
                WritingStylePresentation.Action.CURRENT_DEFAULT));
        assertEquals("设为默认", WritingStylePresentation.actionLabel(
                WritingStylePresentation.Action.SET_DEFAULT));
        assertEquals("保存为新版本并设为默认", WritingStylePresentation.actionLabel(
                WritingStylePresentation.Action.SAVE_AS_NEW_DEFAULT));
    }

    @Test public void listCopyUsesFirstLineAndCompactPreview() {
        assertEquals("微信公众号文体", WritingStylePresentation.displayName("微信公众号文体\n短句"));
        assertEquals("第一行 第二行", WritingStylePresentation.preview("第一行\n第二行"));
    }
}
