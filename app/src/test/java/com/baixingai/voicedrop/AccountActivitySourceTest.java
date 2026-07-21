package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class AccountActivitySourceTest {
    @Test
    public void accountScreenUsesBouncyScrollView() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/AccountActivity.java");

        assertTrue(source.contains("import com.baixingai.voicedrop.ui.BouncyScrollView;"));
        assertTrue(source.contains("BouncyScrollView scroll = new BouncyScrollView(this);"));
        assertFalse(source.contains("import android.widget.ScrollView;"));
        assertFalse(source.contains("ScrollView scroll = new ScrollView(this);"));
        assertFalse(source.contains("scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);"));
    }

    @Test
    public void keepsExistingAccountTokenImportEntry() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/AccountActivity.java");

        assertTrue(source.contains("登录已有账号"));
        assertTrue(source.contains("showTokenImport"));
        assertTrue(source.contains("auth.adoptToken"));
        assertFalse(source.contains("TextView shield = text(\"✓\""));
        assertFalse(source.contains("shield.setBackground(round(Theme.INK, 12))"));
        assertTrue(source.indexOf("card.addView(wechatAuthRow()")
                < source.indexOf("card.addView(existingAccountRow(), existingLp)"));
        assertTrue(source.contains("card.addView(wechatAuthRow(), new LinearLayout.LayoutParams(-1, dp(28)))"));
        assertTrue(source.contains("existingLp.setMargins(0, dp(3), 0, 0)"));
        assertTrue(source.contains("new LinearLayout.LayoutParams(-1, dp(28))"));
        assertTrue(source.contains("row.setMinimumHeight(dp(28))"));
        String existingRow = source.substring(source.indexOf("private View existingAccountRow()"),
                source.indexOf("private View wechatAuthRow()"));
        assertTrue(existingRow.contains("R.drawable.ic_login_existing"));
        assertTrue(existingRow.contains("ImageView.ScaleType.FIT_CENTER"));
        assertTrue(existingRow.contains("new LinearLayout.LayoutParams(dp(18), dp(18))"));
        assertFalse(existingRow.contains("setBackground"));
        assertFalse(source.contains("card.addView(dividerWide(dp(16), dp(16)));\n\n        TextView importButton"));
    }

    @Test
    public void accountIdentityUsesTheCurrentLocalScopeWithoutWaitingForNetwork() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/AccountActivity.java");

        assertTrue(source.contains("currentAccountId = accountIdFromScope(auth.storageScope())"));
        assertTrue(source.indexOf("currentAccountId = accountIdFromScope(auth.storageScope())")
                < source.indexOf("render(0, 0, false)"));
        assertFalse(source.contains("library.ownerScope()"));
        assertTrue(source.contains("currentAccountId"));
        assertTrue(source.contains("keyField(\"你的 ID\", currentAccountId, false, currentAccountId)"));
        assertFalse(source.contains("currentAccountId.isEmpty() ? \"读取失败\""));
        assertTrue(source.contains("keyField(\"访问令牌\", maskedToken(), true, auth.anonymousBearer())"));
        assertTrue(source.contains("auth.adoptToken"));
        assertEquals("wechat-current", AccountActivity.accountIdFromScope("users/wechat-current/"));
    }

    @Test
    public void existingAccountIconUsesProvidedLoginSvg() throws Exception {
        String icon = readSource("src/main/res/drawable/ic_login_existing.xml");

        assertTrue(icon.contains("android:viewportWidth=\"1024\""));
        assertTrue(icon.contains("android:viewportHeight=\"1024\""));
        assertTrue(icon.contains("android:fillColor=\"#666666\""));
        assertTrue(icon.contains("M832 896H298.666667"));
        assertTrue(icon.contains("M601.173333 352"));
        assertTrue(icon.contains("112.213333"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
