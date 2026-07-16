package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SettingsUpdateRowSourceTest {
    @Test
    public void updateVersionIsVerticallyCenteredAndRightAlignedBesideChevron() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String method = methodBody(source, "private TextView addCardRowWithValue");

        assertTrue(method.contains("row.setBaselineAligned(false)"));
        assertTrue(method.contains("valueText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL)"));
        assertTrue(method.contains("valueText.setIncludeFontPadding(false)"));
        assertTrue(method.contains("valueLp.gravity = Gravity.CENTER_VERTICAL"));
        assertTrue(method.contains("row.addView(valueText, valueLp)"));
        assertTrue(method.contains("row.addView(settingsChevron())"));
        assertFalse(method.contains("text(\"›\""));
        assertFalse(method.contains("LinearLayout rightBox"));
    }

    @Test
    public void settingsUsesOneVectorChevronForEveryTrailingAction() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String helper = methodBody(source, "private ImageView settingsChevron");

        assertFalse(source.contains("text(\"›\""));
        assertTrue(helper.contains("chevron.setImageResource(R.drawable.ic_chevron_right_flat)"));
        assertTrue(helper.contains("chevron.setColorFilter(0xffcfc6b6)"));
        assertTrue(helper.contains("new LinearLayout.LayoutParams(dp(16), dp(16))"));
    }

    @Test
    public void accountShortIdSitsImmediatelyBeforeChevron() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String method = methodBody(source, "private void addPrimaryCard");

        assertFalse(method.contains("accountTexts.addView(idText)"));
        int id = method.indexOf("accountRow.addView(idText, idLp)");
        int chevron = method.indexOf("accountRow.addView(settingsChevron())");
        assertTrue(id >= 0);
        assertTrue(chevron > id);
    }

    @Test
    public void primaryUsageRowMatchesIosBalanceAndArticleCapacity() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String primary = methodBody(source, "private void addPrimaryCard");
        String load = methodBody(source, "private void loadPrimaryUsageBalance");

        assertTrue(source.contains("private UsageStore usageStore"));
        assertTrue(primary.contains("text(\"余额与消耗明细\", 12"));
        assertTrue(primary.contains("usageRow.addView(usageBalanceText"));
        assertTrue(load.contains("UsageStore.Balance balance = usageStore.balance()"));
        assertTrue(load.contains("String.valueOf((int) Math.round(balance.suanli))"));
        assertTrue(load.contains("\"约可成文 \" + UsageStore.articleCapacity(balance.suanli) + \" 篇\""));
    }

    @Test
    public void cardDividersStartAtSettingsTextColumn() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String primary = methodBody(source, "private void addPrimaryCard");
        String divider = methodBody(source, "private void addCardDivider");
        String layoutParams = methodBody(source, "private LinearLayout.LayoutParams cardDividerLayoutParams");

        assertTrue(layoutParams.contains("new LinearLayout.LayoutParams(-1, dp(1))"));
        assertTrue(layoutParams.contains("lp.setMargins(dp(56), 0, 0, 0)"));
        assertTrue(primary.contains("card.addView(divider, cardDividerLayoutParams())"));
        assertTrue(divider.contains("card.addView(divider, cardDividerLayoutParams())"));
    }

    @Test
    public void settingsCardsUseIosBorderChrome() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String theme = readSource("src/main/java/com/baixingai/voicedrop/ui/Theme.java");
        String primary = methodBody(source, "private void addPrimaryCard");
        String grouped = methodBody(source, "private void addCard");
        String background = methodBody(source, "private GradientDrawable settingsCardBackground");

        assertTrue(theme.contains("BORDER_CHROME = 0xffece3d5"));
        assertTrue(background.contains("GradientDrawable drawable = round(Theme.CARD, 12)"));
        assertTrue(background.contains("drawable.setStroke(dp(1), Theme.BORDER_CHROME)"));
        assertTrue(primary.contains("card.setBackground(settingsCardBackground())"));
        assertTrue(grouped.contains("card.setBackground(settingsCardBackground())"));
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
