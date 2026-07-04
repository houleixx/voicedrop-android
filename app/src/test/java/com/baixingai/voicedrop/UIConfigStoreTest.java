package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.UIConfigStore;

import org.junit.Test;

import static org.junit.Assert.*;

public class UIConfigStoreTest {
    @Test
    public void parsesLongpressMenusAndFillsPlaceholders() throws Exception {
        String raw = "{\"schema\":1,\"pages\":{\"voice-editor\":{\"longpress\":{\"text\":{\"groups\":[[{\"id\":\"rewrite\",\"label\":\"改写\",\"type\":\"submenu\",\"children\":[{\"id\":\"short\",\"label\":\"短\",\"instruction\":\"第{{LINE}}行 {{QUOTE}}\"}]}]]}}}}}";

        UIConfigStore.UIConfigDoc doc = UIConfigStore.parseDoc(raw);

        UIConfigStore.MenuConfig menu = doc.pages.get("voice-editor").longpress.text;
        assertEquals("rewrite", menu.groups.get(0).get(0).id);
        assertEquals("第3行 开头", UIConfigStore.fill("第{{LINE}}行 {{QUOTE}}", "LINE", "3", "QUOTE", "开头"));
        assertEquals("他说'好'", UIConfigStore.quotePrefix("他说\"好\""));
    }
}
