package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.data.BlockStore;
import com.baixingai.voicedrop.data.CommunityTerms;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.Theme;

import java.util.List;

public final class AboutActivity extends Activity {
    static final int[] ABOUT_ROW_ICON_RES_IDS = {
            R.drawable.ic_about_privacy,
            R.drawable.ic_about_terms,
            R.drawable.ic_about_blocked,
            R.drawable.ic_about_support
    };

    private BlockStore blockStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        blockStore = new BlockStore(this);
        configureEdgeToEdge();

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);
        configureEdgeToEdge();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout top = new FrameLayout(this);
        top.setPadding(dp(12), dp(14) + getStatusBarHeight(), dp(16), dp(10));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout backTouch = new FrameLayout(this);
        backTouch.setClickable(true);
        FrameLayout back = new FrameLayout(this);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(Theme.CARD);
        backBg.setCornerRadius(dp(11));
        backBg.setStroke(dp(1), 0xffe0d8cc);
        back.setBackground(backBg);
        back.setElevation(dp(2));
        ImageView backIcon = new ImageView(this);
        AliIconFont.apply(backIcon, AliIconFont.BACK, Theme.INK);
        backIcon.setScaleType(ImageView.ScaleType.CENTER);
        back.addView(backIcon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        backTouch.addView(back, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        backTouch.setOnClickListener(v -> finishWithPageTransition());
        top.addView(backTouch, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        TextView topTitle = text("关于", 24, Theme.INK, Typeface.BOLD);
        topTitle.setGravity(Gravity.CENTER);
        top.addView(topTitle, new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER));

        BouncyScrollView scroll = new BouncyScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(6), dp(16), dp(28));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addSettingRow(content, R.drawable.ic_about_privacy, "隐私政策", null, this::openPrivacyPolicy);
        addSettingRow(content, R.drawable.ic_about_terms, "社区公约", null, () -> IosDialog.show(this, "社区公约", CommunityTerms.BODY));
        addSettingRow(content, R.drawable.ic_about_blocked, "已屏蔽用户", blockStore.blockedList().size() + " 人", this::showBlockedUsers);
        addSettingRow(content, R.drawable.ic_about_support, "联系我们 / 内容投诉", CommunityTerms.SUPPORT_EMAIL, this::contactSupport);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    @Override
    public void onBackPressed() {
        finishWithPageTransition();
    }

    private void contactSupport() {
        Intent intent = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + CommunityTerms.SUPPORT_EMAIL + "?subject=VoiceDrop%20反馈与投诉"));
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("无法打开邮件客户端");
        }
    }

    private void openPrivacyPolicy() {
        try {
            PrivacyPolicyActivity.open(this);
        } catch (RuntimeException e) {
            toast("暂时无法打开隐私政策");
        }
    }

    private void showBlockedUsers() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), 0, dp(16), 0);
        List<String> blocked = blockStore.blockedList();
        if (blocked.isEmpty()) {
            TextView empty = text("还没有屏蔽任何人", 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, dp(20));
            list.addView(empty);
        } else {
            for (String name : blocked) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(13), dp(16), dp(13));
                row.setBackground(round(Theme.CARD, 12));
                row.addView(text(name, 16, Theme.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
                TextView unblock = text("取消屏蔽", 14, Theme.RED, Typeface.BOLD);
                row.addView(unblock);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, dp(8));
                list.addView(row, lp);
                unblock.setOnClickListener(v -> {
                    blockStore.unblock(name);
                    showBlockedUsers();
                });
            }
        }
        IosDialog.show(this, "已屏蔽用户", list, "关闭", () -> {});
    }

    private void addSettingRow(LinearLayout content, int iconResId, String title, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackground(round(Theme.CARD, 12));
        row.addView(settingIcon(iconResId));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        texts.addView(text(title, 16, Theme.INK, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 12, Theme.SECONDARY, Typeface.NORMAL);
            sub.setPadding(0, dp(4), 0, 0);
            texts.addView(sub);
        }
        row.addView(text("›", 28, 0xffcfc6b6, Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(row, lp);
        row.setOnClickListener(v -> action.run());
    }

    private ImageView settingIcon(int iconResId) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResId);
        icon.setColorFilter(Theme.ACCENT);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28));
        lp.setMargins(0, 0, dp(12), 0);
        icon.setLayoutParams(lp);
        return icon;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : (int) (24 * metrics.density);
    }

    private void configureEdgeToEdge() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Theme.BG);
        }
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void toast(String message) {
        runOnUiThread(() -> SimpleToast.show(this, message));
    }
}
