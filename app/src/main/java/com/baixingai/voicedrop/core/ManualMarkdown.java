package com.baixingai.voicedrop.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dependency-free parser for the bundled VoiceDrop manual. */
public final class ManualMarkdown {
    public enum Kind { TITLE, CHAPTER, SECTION, PARAGRAPH, BULLETS, NUMBERED, TABLE, CODE }

    public static final class Block {
        public final Kind kind;
        public final String text;
        public final List<String> items;
        public final List<List<String>> rows;

        private Block(Kind kind, String text, List<String> items, List<List<String>> rows) {
            this.kind = kind;
            this.text = text == null ? "" : text;
            this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
            this.rows = rows == null ? Collections.emptyList() : Collections.unmodifiableList(rows);
        }

        private static Block text(Kind kind, String text) {
            return new Block(kind, text, null, null);
        }

        private static Block items(Kind kind, List<String> items) {
            return new Block(kind, "", new ArrayList<>(items), null);
        }

        private static Block table(List<String> header, List<List<String>> rows) {
            List<List<String>> copiedRows = new ArrayList<>();
            for (List<String> row : rows) copiedRows.add(new ArrayList<>(row));
            return new Block(Kind.TABLE, "", new ArrayList<>(header), copiedRows);
        }
    }

    private ManualMarkdown() {}

    public static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        List<String> paragraph = new ArrayList<>();
        List<String> bullets = new ArrayList<>();
        List<String> numbered = new ArrayList<>();
        List<List<String>> table = new ArrayList<>();
        List<String> code = new ArrayList<>();
        boolean inCode = false;

        String[] lines = (markdown == null ? "" : markdown).replace("\r\n", "\n").split("\n", -1);
        for (String raw : lines) {
            String line = raw.trim();
            if (inCode) {
                if (line.startsWith("```")) {
                    blocks.add(Block.text(Kind.CODE, String.join("\n", code)));
                    code.clear();
                    inCode = false;
                } else {
                    code.add(raw);
                }
                continue;
            }
            if (line.startsWith("```")) {
                flush(blocks, paragraph, bullets, numbered, table);
                inCode = true;
                continue;
            }
            if (line.isEmpty()) {
                flush(blocks, paragraph, bullets, numbered, table);
                continue;
            }
            if (line.startsWith("### ")) {
                flush(blocks, paragraph, bullets, numbered, table);
                blocks.add(Block.text(Kind.SECTION, line.substring(4)));
                continue;
            }
            if (line.startsWith("## ")) {
                flush(blocks, paragraph, bullets, numbered, table);
                blocks.add(Block.text(Kind.CHAPTER, line.substring(3)));
                continue;
            }
            if (line.startsWith("# ")) {
                flush(blocks, paragraph, bullets, numbered, table);
                blocks.add(Block.text(Kind.TITLE, line.substring(2)));
                continue;
            }
            if (line.startsWith("|")) {
                List<String> cells = tableCells(line);
                if (isTableDivider(cells)) continue;
                flushParagraphAndLists(blocks, paragraph, bullets, numbered);
                table.add(cells);
                continue;
            }
            if (line.startsWith("- ")) {
                flushParagraphAndNumbered(blocks, paragraph, numbered);
                flushTable(blocks, table);
                bullets.add(line.substring(2));
                continue;
            }
            if (line.matches("^\\d+\\.\\s+.*")) {
                flushParagraphAndBullets(blocks, paragraph, bullets);
                flushTable(blocks, table);
                numbered.add(line.replaceFirst("^\\d+\\.\\s+", ""));
                continue;
            }
            flushListsAndTable(blocks, bullets, numbered, table);
            paragraph.add(line);
        }
        flush(blocks, paragraph, bullets, numbered, table);
        if (inCode && !code.isEmpty()) blocks.add(Block.text(Kind.CODE, String.join("\n", code)));
        return blocks;
    }

    public static String inlineHtml(String value) {
        String text = escape(value == null ? "" : value);
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        text = text.replaceAll("`([^`]+)`", "<code>$1</code>");
        return text.replaceAll("\\[([^]]+)]\\((https?://[^)]+)\\)", "<a href='$2'>$1</a>");
    }

    private static void flush(List<Block> blocks, List<String> paragraph, List<String> bullets,
                              List<String> numbered, List<List<String>> table) {
        flushParagraph(blocks, paragraph);
        flushBullets(blocks, bullets);
        flushNumbered(blocks, numbered);
        flushTable(blocks, table);
    }

    private static void flushParagraphAndLists(List<Block> blocks, List<String> paragraph,
                                                List<String> bullets, List<String> numbered) {
        flushParagraph(blocks, paragraph);
        flushBullets(blocks, bullets);
        flushNumbered(blocks, numbered);
    }

    private static void flushParagraphAndNumbered(List<Block> blocks, List<String> paragraph,
                                                   List<String> numbered) {
        flushParagraph(blocks, paragraph);
        flushNumbered(blocks, numbered);
    }

    private static void flushParagraphAndBullets(List<Block> blocks, List<String> paragraph,
                                                  List<String> bullets) {
        flushParagraph(blocks, paragraph);
        flushBullets(blocks, bullets);
    }

    private static void flushListsAndTable(List<Block> blocks, List<String> bullets,
                                           List<String> numbered, List<List<String>> table) {
        flushBullets(blocks, bullets);
        flushNumbered(blocks, numbered);
        flushTable(blocks, table);
    }

    private static void flushParagraph(List<Block> blocks, List<String> paragraph) {
        if (paragraph.isEmpty()) return;
        blocks.add(Block.text(Kind.PARAGRAPH, String.join(" ", paragraph)));
        paragraph.clear();
    }

    private static void flushBullets(List<Block> blocks, List<String> bullets) {
        if (bullets.isEmpty()) return;
        blocks.add(Block.items(Kind.BULLETS, bullets));
        bullets.clear();
    }

    private static void flushNumbered(List<Block> blocks, List<String> numbered) {
        if (numbered.isEmpty()) return;
        blocks.add(Block.items(Kind.NUMBERED, numbered));
        numbered.clear();
    }

    private static void flushTable(List<Block> blocks, List<List<String>> table) {
        if (table.isEmpty()) return;
        List<String> header = table.get(0);
        List<List<String>> rows = table.size() > 1
                ? new ArrayList<>(table.subList(1, table.size())) : Collections.emptyList();
        blocks.add(Block.table(header, rows));
        table.clear();
    }

    private static List<String> tableCells(String line) {
        String clean = line.replaceFirst("^\\|", "").replaceFirst("\\|$", "");
        String[] values = clean.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (String value : values) cells.add(value.trim());
        return cells;
    }

    private static boolean isTableDivider(List<String> cells) {
        if (cells.isEmpty()) return false;
        for (String cell : cells) {
            if (cell.isEmpty() || !cell.matches("^:?-{3,}:?$")) return false;
        }
        return true;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
