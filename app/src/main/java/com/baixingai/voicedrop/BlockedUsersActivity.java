package com.baixingai.voicedrop;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.data.BlockStore;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

import java.util.List;

/** Dedicated local management page for community-blocked authors. */
public final class BlockedUsersActivity extends Activity {
    private BlockStore blockStore;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        blockStore = new BlockStore(this);
        configureEdgeToEdge();

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        page.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, -2));

        BouncyScrollView scroll = new BouncyScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        SystemBarDefaults.applyBottomInsets(content, dp(16), dp(6), dp(16), dp(40));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        renderBlockedUsers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) renderBlockedUsers();
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

    private View buildTopBar() {
        FrameLayout top = new FrameLayout(this);
        SystemBarDefaults.applyTopInsets(top, dp(12), dp(8), dp(16), dp(8));

        FrameLayout backTouch = new FrameLayout(this);
        backTouch.setClickable(true);
        backTouch.setContentDescription("返回");
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

        TextView title = text("社区屏蔽管理", 24, Theme.INK, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER));
        return top;
    }

    private void renderBlockedUsers() {
        content.removeAllViews();
        List<String> authors = blockStore.blockedList();
        if (authors.isEmpty()) {
            LinearLayout empty = card();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(18), dp(16), dp(18));
            empty.addView(text("没有已屏蔽的作者", 14, Theme.SECONDARY, Typeface.NORMAL));
            content.addView(empty, cardLayoutParams());
            return;
        }

        LinearLayout list = card();
        list.setClipToPadding(false);
        for (int i = 0; i < authors.size(); i++) {
            String author = authors.get(i);
            if (i > 0) list.addView(divider());
            list.addView(blockedRow(author));
        }
        content.addView(list, cardLayoutParams());
    }

    private View blockedRow(String author) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView name = text(author, 16, Theme.INK, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        TextView unblock = text("取消屏蔽", 13, Theme.ACCENT, Typeface.BOLD);
        unblock.setGravity(Gravity.CENTER);
        unblock.setMinWidth(dp(88));
        unblock.setMinHeight(dp(36));
        unblock.setPadding(dp(10), 0, dp(10), 0);
        unblock.setBackground(round(Theme.ACCENT_SOFT, 7));
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-2, -2);
        actionLp.setMargins(dp(12), 0, 0, 0);
        row.addView(unblock, actionLp);
        unblock.setOnClickListener(v -> unblock(author));
        return row;
    }

    private void unblock(String author) {
        blockStore.unblock(author);
        renderBlockedUsers();
        SimpleToast.show(this, "已取消屏蔽");
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground());
        return card;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Theme.BORDER_CHROME);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(dp(16), 0, dp(16), 0);
        divider.setLayoutParams(lp);
        return divider;
    }

    private LinearLayout.LayoutParams cardLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(16));
        return lp;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable drawable = round(Theme.CARD, 12);
        drawable.setStroke(dp(1), Theme.BORDER_CHROME);
        return drawable;
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

    private void configureEdgeToEdge() {
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.stay, R.anim.slide_out_right);
    }
}
