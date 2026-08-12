package com.baixingai.voicedrop;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.assertTrue;

/** Protects the Android writing screen's parity with iOS BookWritingSheet. */
public final class BookWritingVisualContractTest {
    @Test public void writingPageKeepsIosLayoutAndStandardPageMotion() throws Exception {
        String page = read("BookWritingActivity.java");
        String shelf = read("ui/BooksShelfPanel.java");
        assertTrue(page.contains("overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)"));
        assertTrue(page.contains("overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)"));
        assertTrue(shelf.contains("BookWritingActivity.open((Activity) getContext())"));
        assertTrue(page.contains("new PageTitleBar(this, \"写书\", this::finishWithPageTransition)"));
        assertTrue(page.contains("roundWithStroke(AMBER_SOFT, 8, 0xffebd9b8, 1)"));
        assertTrue(page.contains("POWER_ICON_RES_ID = R.drawable.ic_settings_bolt"));
        assertTrue(page.contains("powerIcon.setColorFilter(Theme.AMBER)"));
        assertTrue(page.contains("new AbsoluteSizeSpan(14, true)"));
        assertTrue(page.contains("seed.setTextSize(16)"));
        assertTrue(page.contains("seed.setBackground(roundWithStroke(Theme.CARD, 8, Theme.ACCENT, 2))"));
        assertTrue(page.contains("card.addView(divider())"));
        assertTrue(page.contains("row.setGravity(Gravity.TOP)"));
        assertTrue(page.contains("earnRow(R.drawable.ic_settings_bolt, Theme.AMBER, Theme.AMBER_BG"));
        assertTrue(page.contains("earnRow(R.drawable.ic_settings_community, Theme.ACCENT, Theme.ACCENT_SOFT"));
        assertTrue(page.contains("icon.setPadding(dp(10), dp(10), dp(10), dp(10))"));
        assertTrue(page.contains("new LinearLayout.LayoutParams(dp(42), dp(42))"));
        assertTrue(page.contains("SystemBarDefaults.applyBottomInsets(bottom"));
        assertTrue(page.contains("if (loadedBalance != null && loadedBalance < PRICE)"));
        assertTrue(page.contains("format(gap) + \" 算力，两条来路：\""));
        assertTrue(page.indexOf(".balance().suanli") < page.indexOf("new ReferralManager(this).inviteLink()"));
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
