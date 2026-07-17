package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class SettingsActivitySourceTest {
    @Test
    public void settingsScreenUsesBouncyScrollView() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String bouncySource = readSource("src/main/java/com/baixingai/voicedrop/ui/BouncyScrollView.java");

        assertTrue(source.contains("import com.baixingai.voicedrop.ui.BouncyScrollView;"));
        assertTrue(source.contains("BouncyScrollView scroll = new BouncyScrollView(this);"));
        assertFalse(source.contains("import android.widget.ScrollView;"));
        assertFalse(source.contains("ScrollView scroll = new ScrollView(this);"));
        assertTrue(bouncySource.contains("extends ScrollView"));
        assertTrue(bouncySource.contains("setOverScrollMode(OVER_SCROLL_NEVER)"));
        assertTrue(bouncySource.contains("setTranslationY"));
        assertTrue(bouncySource.contains("DecelerateInterpolator"));
    }

    @Test
    public void writingStyleSheetExposesMultiStyleSelection() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");

        assertTrue(source.contains("多风格对比"));
        assertTrue(source.contains("settingsStore.loadStyleHistory()"));
        assertTrue(source.contains("settingsStore.saveStyleSelection(snapshot)"));
        assertTrue(source.contains("最多选择 3 个风格版本"));
        assertTrue(source.contains("selectedStyles.clear()"));
        assertTrue(source.contains("IosDialog.showBottomSheet(this, \"写作风格\", form, 560"));
        assertTrue(source.contains("FrameLayout editorFrame = new FrameLayout(this)"));
        assertTrue(source.contains("frameLp.setMargins(0, dp(12), 0, 0)"));
        assertTrue(source.contains("final boolean[] listOpen = {true}"));
        assertTrue(source.contains("final boolean[] styleLoading = {true}"));
        assertTrue(source.contains("new LoadingStateView(this, \"正在加载写作风格...\""));
        assertTrue(source.contains("toast(\"写作风格加载失败：\" + e.getMessage())"));
        assertTrue(source.contains("listOpen[0] = !listOpen[0]"));
        assertTrue(source.contains("pill.setBackground(round(Theme.ACCENT, 7))"));
        assertTrue(source.contains("bg.setStroke(dp(1), 0xffb9b0a6)"));
        assertTrue(source.contains("card.setElevation(dp(8))"));
        assertTrue(source.contains("new LinearLayout.LayoutParams(-1, dp(56))"));
        assertTrue(source.contains("rowIndex == 0 ? dp(8) : dp(2)"));
        assertTrue(source.contains("rowIndex == validRows - 1 ? dp(8) : dp(2)"));
        assertFalse(source.contains("if (selected) rowLp.setMargins"));
        assertTrue(source.contains("R.drawable.ic_chevron_up_flat"));
        assertTrue(source.contains("R.drawable.ic_chevron_down_flat"));
        assertTrue(source.contains("R.drawable.ic_checkbox_checked_flat"));
        assertTrue(source.contains("R.drawable.ic_checkbox_unchecked_flat"));
        assertTrue(source.contains("new LinearLayout.LayoutParams(dp(28), dp(28))"));
        assertTrue(source.contains("R.drawable.ic_check_flat"));
        assertFalse(source.contains("☑"));
        assertFalse(source.contains("☐"));
    }

    @Test
    public void settingsRemovesFollowupSwitchAndHidesClassicRecorderEscapeHatch() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String recordings = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertFalse(source.contains("成文后追问"));
        assertFalse(source.contains("addFollowupsSwitchRow"));
        assertFalse(source.contains("经典录音引擎"));
        assertFalse(source.contains("addClassicRecorderSwitchRow"));
        assertTrue(recordings.contains("prefs.classicRecorder()"));
    }

    @Test
    public void settingsExposesProfileNameEditor() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");

        assertTrue(source.contains("\"名字\""));
        assertTrue(source.contains("showNameEditor"));
        assertTrue(source.contains("settingsStore.saveName"));
        assertTrue(source.contains("这个名字会出现在文章署名"));
    }

    @Test
    public void profileNameHintUsesSingleLineEllipsis() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String method = methodBody(source, "private void showNameEditor");

        assertTrue(method.contains("hint.setSingleLine(true)"));
        assertTrue(method.contains("hint.setEllipsize(TextUtils.TruncateAt.END)"));
    }

    @Test
    public void settingsShowsCurrentNameOnNameRow() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");

        assertTrue(source.contains("private TextView nameValueText"));
        assertTrue(source.contains("addCardRowWithValue(card, R.drawable.ic_settings_name_card, \"名字\""));
        assertTrue(source.contains("loadNameRowValue()"));
        assertTrue(source.contains("nameValueText.setText(style.name"));
        assertTrue(source.contains("nameValueText.setText(name)"));
    }

    @Test
    public void inviteFriendsUsesItsOwnCard() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String primary = methodBody(source, "private void addPrimaryCard");
        String invite = methodBody(source, "private void addInviteCard");

        assertTrue(source.contains("addPrimaryCard(content);\n        addInviteCard(content);"));
        assertFalse(primary.contains("邀请好友"));
        assertTrue(invite.contains("card.setBackground(settingsCardBackground())"));
        assertTrue(invite.contains("\"邀请好友\", \"朋友装上，双方都得算力\", this::shareInvite"));
        assertTrue(invite.contains("lp.setMargins(0, dp(12), 0, 0)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method: " + signature, start >= 0);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(start, i + 1);
            }
        }
        fail("Unclosed method: " + signature);
        return "";
    }
}
