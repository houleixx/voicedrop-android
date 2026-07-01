package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Community EULA / 社区公约. Matches iOS {@code CommunityTerms}: agreed flag
 * stored in SharedPreferences, shown before a user's first community post.
 */
public final class CommunityTerms {
    private static final String AGREED_KEY = "vd.communityTermsAgreed";
    public static final String SUPPORT_EMAIL = "jianshuo@hotmail.com";

    public static final String BODY =
            "发布到 VD社区，表示你同意以下社区公约：\n\n"
            + "• 你对自己发布的内容负责，并拥有发布它的权利。\n"
            + "• 严禁发布令人反感的内容——包括色情或露骨性内容、暴力血腥、仇恨或歧视、骚扰或欺凌、违法内容、自残等。VoiceDrop 对令人反感的内容和滥用行为零容忍。\n"
            + "• 违规内容一经举报将被立即下架，并在 24 小时内处理；屡次或严重违规的账号将被移除。\n"
            + "• 你可以随时举报不当内容、屏蔽不想看到的用户。\n\n"
            + "继续即表示你已阅读并同意本社区公约与最终用户许可协议（EULA）。如需联系或投诉内容，请发邮件至 "
            + SUPPORT_EMAIL + "。";

    private final SharedPreferences prefs;

    public CommunityTerms(Context context) {
        this.prefs = context.getSharedPreferences("vd_community_terms", Context.MODE_PRIVATE);
    }

    public boolean agreed() {
        return prefs.getBoolean(AGREED_KEY, false);
    }

    public void setAgreed(boolean agreed) {
        prefs.edit().putBoolean(AGREED_KEY, agreed).apply();
    }
}
