package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfiguredMenuSourceTest {
    @Test
    public void submenuReopensPopupAtTheOriginalImageAnchor() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("showConfiguredPopup(sub, anchor)"));
        assertTrue(source.contains("if (popup[0] != null) popup[0].dismiss();"));
        assertFalse(source.contains("popup[0].setContentView(sub)"));
    }

    @Test
    public void submenuRowsUseTheFlatRightChevronInsteadOfTheMoreIcon() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String icons = readSource("src/main/java/com/baixingai/voicedrop/ui/AliIconFont.java");

        assertTrue(source.contains("menuRow(node.label, AliIconFont.CHEVRON_RIGHT_FLAT"));
        assertTrue(icons.contains("CHEVRON_RIGHT_FLAT = R.drawable.ic_chevron_right_flat"));
    }

    @Test
    public void imageStyleRowsUseSemanticIconsByStableIdWithPaperPlaneFallback() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String icons = readSource("src/main/java/com/baixingai/voicedrop/ui/AliIconFont.java");

        assertTrue(source.contains("menuRow(node.label, configuredInstructionIcon(node.id)"));
        assertTrue(source.contains("case \"cartoon\": return AliIconFont.STYLE_CARTOON"));
        assertTrue(source.contains("case \"ad\": return AliIconFont.STYLE_AD"));
        assertTrue(source.contains("case \"watercolor\": return AliIconFont.STYLE_WATERCOLOR"));
        assertTrue(source.contains("case \"sketch\": return AliIconFont.STYLE_SKETCH"));
        assertTrue(source.contains("case \"oil\": return AliIconFont.STYLE_OIL"));
        assertTrue(source.contains("case \"film\": return AliIconFont.STYLE_FILM"));
        assertTrue(source.contains("default: return AliIconFont.PAPERPLANE"));
        assertTrue(icons.contains("STYLE_CARTOON = R.drawable.ic_style_cartoon"));
        assertTrue(icons.contains("STYLE_AD = R.drawable.ic_style_ad"));
        assertTrue(icons.contains("STYLE_WATERCOLOR = R.drawable.ic_style_watercolor"));
        assertTrue(icons.contains("STYLE_SKETCH = R.drawable.ic_style_sketch"));
        assertTrue(icons.contains("STYLE_OIL = R.drawable.ic_style_oil"));
        assertTrue(icons.contains("STYLE_FILM = R.drawable.ic_style_film"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
