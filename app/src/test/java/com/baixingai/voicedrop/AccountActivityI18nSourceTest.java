package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class AccountActivityI18nSourceTest {
    @Test
    public void translatesWechatSignedInStatusForEnglish() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/I18n.java");

        assertTrue(source.contains("copy.put(\"已用微信登录\", \"Signed in with WeChat\");"));
    }

    @Test
    public void leavesBreathingRoomBelowTheWechatAccountRow() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/AccountActivity.java");

        assertTrue(source.contains("card.addView(dividerWide(auth.isWechatAuthenticated() ? dp(10) : dp(8), dp(12)));"));
    }

    @Test
    public void localizesAccountAndCreditCountsWithFormatStrings() throws Exception {
        String account = readSource("src/main/java/com/baixingai/voicedrop/AccountActivity.java");
        String usage = readSource("src/main/java/com/baixingai/voicedrop/UsageActivity.java");
        String i18n = readSource("src/main/java/com/baixingai/voicedrop/ui/I18n.java");

        assertTrue(account.contains("I18n.format(this, \"%d 条\", recordingCount)"));
        assertTrue(account.contains("I18n.format(this, \"%d 篇\", minedCount)"));
        assertTrue(usage.contains("I18n.format(this, \"≈ %d 篇\","));
        assertTrue(usage.contains("I18n.format(this, \"累计获赠 %d · 已用 %d\","));
        assertTrue(i18n.contains("copy.put(\"%d 条\", \"%d items\");"));
        assertTrue(i18n.contains("copy.put(\"%d 篇\", \"%d articles\");"));
        assertTrue(i18n.contains("copy.put(\"≈ %d 篇\", \"≈ %d articles\");"));
        assertTrue(i18n.contains("copy.put(\"累计获赠 %d · 已用 %d\", \"Total granted %d · Used %d\");"));
    }

    @Test
    public void localizesCreditReasonsCountsAndDates() throws Exception {
        String usage = readSource("src/main/java/com/baixingai/voicedrop/UsageActivity.java");
        String i18n = readSource("src/main/java/com/baixingai/voicedrop/ui/I18n.java");

        assertTrue(usage.contains("I18n.format(this, \"%d 笔\", rowData.count)"));
        assertTrue(usage.contains("DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,"));
        assertTrue(usage.contains("I18n.locale()).format(new Date(e.ts))"));
        assertTrue(i18n.contains("copy.put(\"%d 笔\", \"%d entries\");"));
        assertTrue(i18n.contains("copy.put(\"投币奖励\", \"Coin rewards\");"));
        assertTrue(i18n.contains("copy.put(\"收到投币\", \"Coins received\");"));
        assertTrue(i18n.contains("copy.put(\"修书\", \"Book revisions\");"));
        assertTrue(i18n.contains("copy.put(\"AI 采访\", \"AI interviews\");"));
        assertTrue(i18n.contains("copy.put(\"文风蒸馏\", \"Style distillation\");"));
        assertTrue(i18n.contains("copy.put(\"图片编辑\", \"Image editing\");"));
        assertTrue(i18n.contains("copy.put(\"小红书分享\", \"Xiaohongshu sharing\");"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
