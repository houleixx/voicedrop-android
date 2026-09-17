package com.baixingai.voicedrop.ui;

import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import static org.junit.Assert.assertEquals;

public final class BookShelfCategoryLocaleTest {
    @Test public void categoryLabelsFollowEnglishAndChineseWithoutTranslatingBookTitles() throws Exception {
        String[] keys = {"全部", "我的", "商业", "投资", "AI", "科学", "人文", "身心", "生活", "故事"};
        String[] english = {"All", "Mine", "Business", "Investing", "AI", "Science", "Humanities", "Wellness", "Lifestyle", "Stories"};
        // Exercise the real catalog without invoking AppCompat's Android-only recreation path.
        Field current = I18n.class.getDeclaredField("current");
        current.setAccessible(true);
        Object original = current.get(null);
        Method from = current.getType().getDeclaredMethod("from", Locale.class, String.class);
        from.setAccessible(true);
        try {
            for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH}) {
                current.set(null, from.invoke(null, locale, locale.toLanguageTag()));
                for (int i = 0; i < keys.length; i++) {
                    assertEquals(locale.equals(Locale.ENGLISH) ? english[i] : keys[i], I18n.text(null, keys[i]));
                }
                assertEquals("自定义中文书名", I18n.text(null, "自定义中文书名"));
                assertEquals("未知分类", I18n.text(null, "未知分类"));
            }
        } finally {
            current.set(null, original);
        }
    }
}
