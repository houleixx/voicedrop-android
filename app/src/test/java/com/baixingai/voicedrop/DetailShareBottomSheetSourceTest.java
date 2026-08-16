package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DetailShareBottomSheetSourceTest {
    @Test public void sharedSheetUsesFourColumnsAndBottomSheetPresentation() throws Exception {
        String source = read("ui/ShareBottomSheet.java");
        assertTrue(source.contains("COLUMN_COUNT = 4"));
        assertTrue(source.contains("IosDialog.showBottomSheet"));
        assertTrue(source.contains("CIRCLE_SIZE_DP = 56"));
        assertTrue(source.contains("ImageView.ScaleType.FIT_CENTER"));
        assertTrue(source.contains("item.iconSizeDp"));
        assertTrue(source.contains("selectableItemBackgroundBorderless"));
        assertTrue(source.contains("FIRST_ROW_EXTRA_BOTTOM_DP = 11"));
        assertTrue(source.contains("ROW_SEPARATOR_HEIGHT_DP = 17"));
        assertTrue(source.contains("index % COLUMN_COUNT == 0"));
        assertTrue(source.contains("setContentDescription(item.label)"));
        assertTrue(source.contains("dialogRef[0].dismissAnimated(item.action)"));
        assertFalse(source.contains("handleArea"));
        assertFalse(source.contains("handleParams"));
        assertFalse(source.contains("dialogRef[0].dismiss();\n                item.action.run();"));
    }

    @Test public void recordingDetailMovesOnlyShareActionsIntoTheSheet() throws Exception {
        String source = read("RecordingDetailActivity.java");
        assertTrue(source.contains("showRecordingShareSheet(rec)"));
        assertFalse(source.contains("true, () -> showRecordingShareSheet(rec)"));
        assertTrue(source.contains("ShareBottomSheet.drawable(\"小程序卡片\""));
        assertTrue(source.contains("ShareBottomSheet.remix(\"朋友圈\""));
        assertTrue(source.contains("ShareBottomSheet.drawable(\"小红书\""));
        assertTrue(source.contains("ShareBottomSheet.drawable(\"复制链接\""));
        assertTrue(source.contains("ShareBottomSheet.drawable(\"其它分享\""));
        assertTrue(source.contains("打开 VoiceDrop 阅读这篇文章"));
        String more = method(source, "protected void showMoreMenu", "protected void shareToXhs");
        assertTrue(more.contains("发布公众号草稿"));
        assertTrue(more.contains("VD 社区可见"));
        assertTrue(more.contains("menuRow(\"分享\""));
        assertTrue(more.contains("showRecordingShareSheet(rec)"));
        assertTrue(more.contains("menuRow(\"删除\""));
        assertTrue(more.indexOf("VD 社区可见") < more.indexOf("menuRow(\"分享\""));
        assertTrue(more.indexOf("menuRow(\"分享\"") < more.indexOf("menuRow(\"删除\""));
        assertFalse(more.contains("分享到小红书"));
        assertFalse(more.contains("分享到微信"));
    }

    @Test public void communityDetailKeepsModerationOutOfTheShareSheet() throws Exception {
        String source = read("CommunityDetailActivity.java");
        assertTrue(source.contains("showCommunityShareSheet(post)"));
        assertFalse(source.contains("0, true, () -> showCommunityShareSheet(post)"));
        assertTrue(source.contains("ShareBottomSheet.drawable(\"小程序卡片\""));
        assertTrue(source.contains("ShareBottomSheet.remix(\"朋友圈\""));
        assertTrue(source.contains("ShareBottomSheet.drawable(\"复制链接\""));
        assertTrue(source.contains("ShareBottomSheet.drawable(\"其它分享\""));
        assertTrue(source.contains("打开 VoiceDrop 查看这篇社区分享"));
        assertFalse(source.contains("ShareBottomSheet.drawable(\"小红书\""));
        String more = method(source, "protected void showCommunityPostMenu", "protected void showReportConfirm");
        assertTrue(more.contains("menuRow(\"写回应\""));
        assertTrue(more.contains("menuRow(\"分享\""));
        assertTrue(more.contains("showCommunityShareSheet(post)"));
        assertTrue(more.contains("menuRow(\"举报\""));
        assertTrue(more.contains("menuRow(\"屏蔽此用户\""));
        assertTrue(more.indexOf("menuRow(\"写回应\"") < more.indexOf("menuRow(\"分享\""));
        assertTrue(more.indexOf("menuRow(\"分享\"") < more.indexOf("menuRow(\"举报\""));
        assertFalse(more.contains("shareCommunityMiniProgramCard"));
        assertFalse(more.contains("shareCommunityUrl"));
    }

    @Test public void bookDetailIncludesCopyLinkInTheShareSheet() throws Exception {
        String source = read("BookReaderActivity.java");
        assertTrue(source.contains("ShareBottomSheet.drawable(\"微信好友\""));
        assertTrue(source.contains("ShareBottomSheet.remix(\"朋友圈\""));
        assertTrue(source.contains("ShareBottomSheet.drawable(\"复制链接\""));
        assertTrue(source.contains("this::copyBookLink"));
        assertTrue(source.contains("ShareBottomSheet.drawable(\"其它分享\""));
        assertTrue(source.contains("ClipData.newPlainText(\"VoiceDrop 书籍链接\", url)"));
    }

    private static String method(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        return source.substring(from, to);
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
