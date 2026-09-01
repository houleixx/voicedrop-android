package com.baixingai.voicedrop.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import com.baixingai.voicedrop.PrivacyPolicyActivity;

public final class PrivacyConsentDialog {
    private static final String POLICY_LABEL = "《隐私政策》";
    private static final String PRIVACY_POLICY_LABEL = "Privacy Policy";

    public static void show(Activity activity, Runnable onAccept, Runnable onDecline) {
        TextView message = new TextView(activity);
        message.setTextSize(15);
        message.setTextColor(Theme.INK);
        message.setLineSpacing(dp(activity, 4), 1f);
        message.setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 18));

        boolean english = I18n.locale().getLanguage().equals("en");
        String policyLabel = english ? PRIVACY_POLICY_LABEL : POLICY_LABEL;
        String copy = english
                ? "Welcome to VoiceDrop. To provide recording, transcription, article creation, and optional sharing, "
                + "we process the recordings, text, and photos you choose to provide.\n\n"
                + "Please read the " + policyLabel + ". The app starts these services only after you agree."
                : "欢迎使用 VoiceDrop。为了提供录音、语音转写、文章生成和可选分享功能，"
                + "我们会处理你主动提供的录音、文字和照片。\n\n"
                + "请阅读并了解" + policyLabel + "。同意后，App 才会启动相关服务。";
        SpannableString text = new SpannableString(copy);
        int start = copy.indexOf(policyLabel);
        text.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                try {
                    PrivacyPolicyActivity.open(activity);
                } catch (RuntimeException e) {
                    SimpleToast.show(activity, "暂时无法打开隐私政策");
                }
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                ds.setColor(Theme.RED);
                ds.setTypeface(Typeface.DEFAULT_BOLD);
                ds.setUnderlineText(false);
            }
        }, start, start + policyLabel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        message.setText(text);
        message.setMovementMethod(LinkMovementMethod.getInstance());
        message.setHighlightColor(0x22df5d49);

        IosDialog.showRequiredChoice(activity, english ? "Privacy notice" : "隐私保护提示", message,
                english ? "Agree and continue" : "同意并继续", onAccept,
                english ? "Disagree and exit" : "不同意并退出", onDecline);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private PrivacyConsentDialog() {}
}
