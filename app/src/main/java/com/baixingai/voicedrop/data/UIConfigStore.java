package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class UIConfigStore {
    private static final String PREFS = "voicedrop.uiconfig";
    private static final String KEY_CACHE = "uiConfigCache.v1";
    private static final int MAX_SCHEMA = 1;

    private final Context context;
    private final AuthStore auth;
    private final HttpClient http;
    private UIConfigDoc doc;

    public UIConfigStore(Context context, AuthStore auth, HttpClient http) {
        this.context = context.getApplicationContext();
        this.auth = auth;
        this.http = http;
        this.doc = loadCached();
    }

    public synchronized MenuConfig imageMenu(String page) {
        return menu(page, "image");
    }

    public synchronized MenuConfig textMenu(String page) {
        return menu(page, "text");
    }

    public void refresh() {
        try {
            HttpClient.Response response = http.get(Api.agentBase() + "/ui-config", auth.bearer());
            if (!response.ok()) return;
            UIConfigDoc fresh = parseDoc(response.text());
            if (fresh.schema > MAX_SCHEMA) return;
            synchronized (this) {
                doc = fresh;
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CACHE, response.text())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public List<InstructionItem> loadCustomItems() throws Exception {
        HttpClient.Response response = http.get(Api.agentBase() + "/ui-config/custom", auth.bearer());
        if (!response.ok()) throw new IllegalStateException("ui-config custom HTTP " + response.code);
        JSONArray arr = new JSONObject(response.text()).optJSONArray("items");
        List<InstructionItem> out = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                out.add(new InstructionItem(
                        obj.optString("id", ""),
                        obj.optString("label", ""),
                        obj.optString("default", ""),
                        obj.isNull("override") ? null : obj.optString("override", null),
                        obj.isNull("customLabel") ? null : obj.optString("customLabel", null),
                        obj.optBoolean("hidden", false),
                        obj.isNull("shareCode") ? null : obj.optString("shareCode", null),
                        obj.optBoolean("sharing", false)));
            }
        }
        return out;
    }

    public void saveCustomItem(String id, String instruction, String label, boolean hidden) throws Exception {
        JSONObject body = new JSONObject()
                .put("id", id)
                .put("instruction", instruction == null ? "" : instruction)
                .put("label", label == null ? "" : label)
                .put("hidden", hidden);
        HttpClient.Response response = http.putBytes(Api.agentBase() + "/ui-config/custom", auth.bearer(),
                "application/json", body.toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("ui-config custom save HTTP " + response.code);
        refresh();
    }

    public ShareState setSharing(String id, boolean sharing) throws Exception {
        String url = Api.agentBase() + "/prompt-share";
        HttpClient.Response response;
        if (sharing) {
            response = http.postJson(url, auth.bearer(), new JSONObject().put("id", id).toString().getBytes("UTF-8"));
        } else {
            response = http.delete(url + "/" + Uri.encode(id), auth.bearer());
        }
        if (!response.ok()) {
            if (response.code == 429) throw new IllegalStateException("今天生成分享码的次数已达上限，明天再试");
            throw new IllegalStateException("提示词分享操作失败");
        }
        JSONObject body = new JSONObject(response.text());
        return new ShareState(body.optString("code", ""), body.optBoolean("sharing", sharing));
    }

    private MenuConfig menu(String page, String kind) {
        UIConfigDoc source = doc == null ? builtin() : doc;
        PageConfig pageConfig = source.pages.get(page);
        if (pageConfig == null || pageConfig.longpress == null) return null;
        return "image".equals(kind) ? pageConfig.longpress.image : pageConfig.longpress.text;
    }

    private UIConfigDoc loadCached() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_CACHE, "");
        if (raw != null && !raw.isEmpty()) {
            try {
                UIConfigDoc cached = parseDoc(raw);
                if (cached.schema <= MAX_SCHEMA) return cached;
            } catch (Exception ignored) {
            }
        }
        return builtin();
    }

    public static String fill(String instruction, String key, String value) {
        return instruction == null ? "" : instruction.replace("{{" + key + "}}", value == null ? "" : value);
    }

    public static String fill(String instruction, String key1, String value1, String key2, String value2) {
        return fill(fill(instruction, key1, value1), key2, value2);
    }

    public static String quotePrefix(String text) {
        if (text == null) return "";
        String trimmed = text.trim().replace('"', '\'');
        return trimmed.length() <= 15 ? trimmed : trimmed.substring(0, 15);
    }

    public static UIConfigDoc parseDoc(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        UIConfigDoc doc = new UIConfigDoc(root.optInt("schema", 1));
        JSONObject pages = root.optJSONObject("pages");
        if (pages == null) return doc;
        JSONArray names = pages.names();
        if (names == null) return doc;
        for (int i = 0; i < names.length(); i++) {
            String pageName = names.optString(i);
            JSONObject pageObj = pages.optJSONObject(pageName);
            if (pageObj == null) continue;
            JSONObject longpressObj = pageObj.optJSONObject("longpress");
            LongpressConfig longpress = null;
            if (longpressObj != null) {
                longpress = new LongpressConfig(parseMenu(longpressObj.optJSONObject("image")),
                        parseMenu(longpressObj.optJSONObject("text")));
            }
            doc.pages.put(pageName, new PageConfig(longpress));
        }
        return doc;
    }

    private static MenuConfig parseMenu(JSONObject obj) {
        if (obj == null) return null;
        MenuConfig menu = new MenuConfig();
        JSONArray groups = obj.optJSONArray("groups");
        if (groups == null) return menu;
        for (int i = 0; i < groups.length(); i++) {
            JSONArray groupArr = groups.optJSONArray(i);
            if (groupArr == null) continue;
            List<MenuNode> group = new ArrayList<>();
            for (int j = 0; j < groupArr.length(); j++) {
                MenuNode node = parseNode(groupArr.optJSONObject(j));
                if (node != null) group.add(node);
            }
            if (!group.isEmpty()) menu.groups.add(group);
        }
        return menu;
    }

    private static MenuNode parseNode(JSONObject obj) {
        if (obj == null) return null;
        MenuNode node = new MenuNode(obj.optString("id", ""), obj.optString("label", ""),
                obj.optString("type", ""), obj.optString("instruction", ""));
        JSONArray children = obj.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                MenuNode child = parseNode(children.optJSONObject(i));
                if (child != null) node.children.add(child);
            }
        }
        return node;
    }

    private static UIConfigDoc builtin() {
        UIConfigDoc doc = new UIConfigDoc(1);
        List<MenuNode> imageGroup = new ArrayList<>();
        MenuNode style = new MenuNode("style", "图片风格", "submenu", "");
        style.children.add(new MenuNode("cartoon", "卡通", "", "把这张图（[[photo:{{KEY}}]]）重画成宫崎骏动画的手绘卡通风格，构图和主体不变，正文其他内容都不要动。"));
        style.children.add(new MenuNode("ad", "广告", "", "把这张图（[[photo:{{KEY}}]]）重新设计成一则商品广告。请从专业设计师的角度，结合本篇文章的内容和受众，打造一个精致、洗练的视觉设计。整体风格要现代、极简，不使用文字，可以加一些别的代替文字的元素。请通过合理的版式构成，最大限度地突出商品的魅力。正文其他内容都不要动。"));
        style.children.add(new MenuNode("watercolor", "水彩", "", "把这张图（[[photo:{{KEY}}]]）重画成通透的水彩画风格，构图和主体不变，正文其他内容都不要动。"));
        style.children.add(new MenuNode("sketch", "素描", "", "把这张图（[[photo:{{KEY}}]]）重画成铅笔素描风格，构图和主体不变，正文其他内容都不要动。"));
        style.children.add(new MenuNode("oil", "油画", "", "把这张图（[[photo:{{KEY}}]]）重画成古典油画风格，构图和主体不变，正文其他内容都不要动。"));
        style.children.add(new MenuNode("film", "胶片", "", "把这张图（[[photo:{{KEY}}]]）调成胶片摄影的质感和色调，构图和主体不变，正文其他内容都不要动。"));
        imageGroup.add(style);
        MenuConfig imageMenu = new MenuConfig();
        imageMenu.groups.add(imageGroup);

        MenuConfig textMenu = new MenuConfig();
        List<MenuNode> rewriteGroup = new ArrayList<>();
        MenuNode rewrite = new MenuNode("rewrite", "改写这段", "submenu", "");
        rewrite.children.add(new MenuNode("concise", "更简洁", "", "把第{{LINE}}行（开头是\"{{QUOTE}}\"）改写得更简洁，意思不变，正文其他行都不要动。"));
        rewrite.children.add(new MenuNode("casual", "更口语", "", "把第{{LINE}}行（开头是\"{{QUOTE}}\"）改写得更口语、像平时说话，意思不变，正文其他行都不要动。"));
        rewrite.children.add(new MenuNode("formal", "更书面", "", "把第{{LINE}}行（开头是\"{{QUOTE}}\"）改写得更书面、更正式，意思不变，正文其他行都不要动。"));
        rewrite.children.add(new MenuNode("expand", "扩写一点", "", "把第{{LINE}}行（开头是\"{{QUOTE}}\"）扩写一点，补充细节但别啰嗦，正文其他行都不要动。"));
        rewriteGroup.add(rewrite);
        textMenu.groups.add(rewriteGroup);
        List<MenuNode> insertGroup = new ArrayList<>();
        MenuNode insert = new MenuNode("insert", "插入图片", "submenu", "");
        insert.children.add(new MenuNode("wechat-cover", "公众号题图", "", "给这篇文章画一张微信公众号题图，放在文章最前面。画面为 2.45:1 的横幅比例。主视觉不要用泛泛的机器人形象或模糊的科技背景，要用具体的物件表达文章主题，比如提示词卡片、设计画布、图片生成面板、封面草稿。题图上的中文主标题从文章标题提炼，必须清晰可读，最好 6 到 10 个汉字。构图要适合公众号封面：大标题放左侧，主视觉放右侧，四周留足安全边距。风格：成熟的新媒体编辑部封面，干净、精致、实用，不要廉价营销海报感。避免：乱码文字、过多小字、真实品牌 logo、纯氛围壁纸、厚重的蓝紫渐变。正文其他内容都不要动。"));
        insertGroup.add(insert);
        textMenu.groups.add(insertGroup);

        doc.pages.put("voice-editor", new PageConfig(new LongpressConfig(imageMenu, textMenu)));
        return doc;
    }

    public static final class UIConfigDoc {
        public final int schema;
        public final java.util.Map<String, PageConfig> pages = new java.util.HashMap<>();

        UIConfigDoc(int schema) {
            this.schema = schema;
        }
    }

    public static final class PageConfig {
        public final LongpressConfig longpress;

        PageConfig(LongpressConfig longpress) {
            this.longpress = longpress;
        }
    }

    public static final class LongpressConfig {
        public final MenuConfig image;
        public final MenuConfig text;

        LongpressConfig(MenuConfig image, MenuConfig text) {
            this.image = image;
            this.text = text;
        }
    }

    public static final class MenuConfig {
        public final List<List<MenuNode>> groups = new ArrayList<>();
    }

    public static final class MenuNode {
        public final String id;
        public final String label;
        public final String type;
        public final String instruction;
        public final List<MenuNode> children = new ArrayList<>();

        MenuNode(String id, String label, String type, String instruction) {
            this.id = id == null ? "" : id;
            this.label = label == null ? "" : label;
            this.type = type == null ? "" : type;
            this.instruction = instruction == null ? "" : instruction;
        }
    }

    public static final class InstructionItem {
        public final String id;
        public final String label;
        public final String defaultText;
        public final String override;
        public final String customLabel;
        public final boolean hidden;
        public final String shareCode;
        public final boolean sharing;

        InstructionItem(String id, String label, String defaultText, String override, String customLabel, boolean hidden,
                        String shareCode, boolean sharing) {
            this.id = id == null ? "" : id;
            this.label = label == null ? "" : label;
            this.defaultText = defaultText == null ? "" : defaultText;
            this.override = override == null || override.isEmpty() ? null : override;
            this.customLabel = customLabel == null || customLabel.isEmpty() ? null : customLabel;
            this.hidden = hidden;
            this.shareCode = shareCode == null || shareCode.isEmpty() ? null : shareCode;
            this.sharing = sharing;
        }

        public String effectiveLabel() {
            return customLabel == null ? label : customLabel;
        }

        public String effectiveInstruction() {
            return override == null ? defaultText : override;
        }

        public boolean customized() {
            return override != null || customLabel != null;
        }
    }

    public static final class ShareState {
        public final String code;
        public final boolean sharing;

        ShareState(String code, boolean sharing) {
            this.code = code;
            this.sharing = sharing;
        }
    }
}
