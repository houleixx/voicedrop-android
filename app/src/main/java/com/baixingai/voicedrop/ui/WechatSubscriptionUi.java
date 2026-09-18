package com.baixingai.voicedrop.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import com.baixingai.voicedrop.R;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.WechatProductActivity;
import com.baixingai.voicedrop.data.WechatSubscriptionStore.Status;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Native subscription surfaces. Signing and payment remain owned by WeChat and the backend. */
public final class WechatSubscriptionUi {
    public static final int PRIMARY = Theme.AMBER;
    public static final int SUCCESS = 0xff00a65a;

    private WechatSubscriptionUi() {}

    public static Dialog showPurchase(Activity activity, Status status, Runnable confirm) {
        LinearLayout body = column(activity);
        body.setPadding(dp(activity, 20), dp(activity, 14), dp(activity, 20), dp(activity, 12));
        centered(body, "VoiceDrop 包月算力", 18, Theme.INK, false);
        centered(body, status.priceLabel(), 32, Theme.INK, true);
        centered(body, "每期 " + number(status.monthlySuanli) + " 算力 · 按订阅周期到期", 13, Theme.SECONDARY, false);
        paymentMethod(body);
        label(body, status.restoreAuthorization ? "本次仅申请自动续费授权，后续续费价格 " + status.priceLabel() + "。" : "首期付款并开通自动续费，订阅价格 " + status.priceLabel() + "，请在微信确认金额。", 13, Theme.SECONDARY, false);
        label(body, status.restoreAuthorization ? status.paymentNotice() : status.renewalAvailable ? "首期在微信确认后支付；续费在到期前第 3 天申请，通常通知 24 小时后扣款。可随时解约，已提交的扣款仍可能完成。" : status.paymentNotice(), 13, Theme.SECONDARY, false);
        link(body, "产品介绍  ›", () -> openProduct(activity));
        LinearLayout rules = column(activity);
        rules.setPadding(0, 0, 0, dp(activity, 8));
        if (status.renewalAvailable && status.contractManagementAvailable) section(rules, "完整购买与自动续费须知",
                (status.restoreAuthorization ? "1. 在微信确认自动续费授权，当前已付算力及有效期不变。如果已到续费申请时间，授权后即可申请下一期扣款。\n\n" : "1. 在微信支付首期金额并开通自动续费。首期付款成功后发放算力，自动续费以签约结果为准。\n\n")
                + "2. 当前续费计划在到期日前第 3 个北京时间自然日凌晨 2 点申请，通常在通知后 24 小时扣款，实际付款可能早于到期日。\n\n"
                + "3. 提前续费成功后，新算力立即可用，有效至下一订阅周期结束；旧算力仍按原到期日失效。\n\n"
                + "4. 可在本页「管理订阅」解除自动续费，也可在微信的扣费服务中管理。卸载应用不会解除签约。\n\n"
                + "5. 解约不自动退款，也不收回已付算力。解约前已提交的扣款仍可能完成，付款成功后会正常发放算力。");
        else section(rules, "当前服务范围", "首期付款成功后获得一个月算力。后续自动续费暂不可用。微信中的授权状态请在微信扣费服务中查看或解除，App 暂不提供解约。卸载 App 不会解除授权。");
        body.addView(rules);

        LinearLayout footer = column(activity);
        footer.setBackgroundColor(Theme.CARD);
        TextView pay = button(footer, status.restoreAuthorization ? "同意并" + status.authorizationActionLabel() : "同意并支付签约", PRIMARY);
        IosDialog dialog = sheetWithFooter(activity, status.restoreAuthorization ? status.authorizationActionLabel() : "确认订阅", body, footer, 520);
        pay.setOnClickListener(v -> {
            if (!pay.isEnabled()) return;
            pay.setEnabled(false);
            dialog.dismissAnimated(confirm);
        });
        return dialog;
    }

    public static Dialog showManagement(Activity activity, Status status, Runnable cancel, Runnable refresh) {
        LinearLayout body = column(activity);
        label(body, status.displayTitle(), 24, Theme.INK, true);
        label(body, status.paymentNotice(), 14, Theme.SECONDARY, false);
        if (status.expiresAt > 0) section(body, "已付权益有效至", date(status.expiresAt));
        section(body, "解约与退款", status.contractManagementAvailable ? "解除后不再发起新扣款，已付算力仍可使用至到期。已提交的订单仍可能完成支付；解除自动续费不会自动退款。" : "请在微信扣费服务中查看或解除授权。App 内解约暂不可用；已付算力仍可使用至到期。");
        section(body, "需要帮助", "扣费或到账有疑问，可联系客服并提供账号标识、付款时间和订单号。请勿提供支付密码或验证码。\njianshuo@hotmail.com");
        LinearLayout links = new LinearLayout(activity);
        boolean stacked = activity.getResources().getConfiguration().fontScale >= 1.3f
                || activity.getResources().getConfiguration().screenWidthDp < 360;
        links.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        links.setPadding(0, dp(activity, 8), 0, 0);
        TextView product = link(links, "产品介绍与续费规则  ›", () -> openProduct(activity));
        TextView refreshLink = link(links, "刷新订阅状态  ↻", refresh);
        product.setLayoutParams(stacked ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1.3f));
        refreshLink.setLayoutParams(stacked ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1f));
        if (!stacked) refreshLink.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        body.addView(links, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout footer = column(activity);
        TextView stop = status.canManageRenewal() ? button(footer, "解除自动续费", PRIMARY) : null;
        IosDialog dialog = sheetWithFooter(activity, status.managementLabel(), body, footer, 520);
        if (stop != null) stop.setOnClickListener(v -> dialog.dismissAnimated(cancel));
        return dialog;
    }

    /** Only call after a newer paid entitlement is observed, never merely after SDK return. */
    public static Dialog showSuccess(Activity activity, Status status) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout page = column(activity);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(activity, 28), dp(activity, 40), dp(activity, 28), dp(activity, 32));
        page.setBackgroundColor(Theme.BG);
        SystemBarDefaults.applyTopAndBottomInsets(page, dp(activity, 28), dp(activity, 40), dp(activity, 28), dp(activity, 32));
        page.addView(new View(activity), new LinearLayout.LayoutParams(1, 0, 0.4f));
        ImageView check = new ImageView(activity);
        check.setImageResource(R.drawable.ic_check_flat);
        check.setImageTintList(ColorStateList.valueOf(0xffffffff));
        check.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 18));
        check.setContentDescription("付款成功");
        check.setBackground(round(activity, SUCCESS, 48));
        LinearLayout.LayoutParams icon = new LinearLayout.LayoutParams(dp(activity, 88), dp(activity, 88));
        icon.topMargin = 0;
        icon.bottomMargin = dp(activity, 22);
        page.addView(check, icon);
        centered(page, "付款成功", 25, Theme.INK, true);
        centered(page, "算力已添加", 30, Theme.INK, true);
        if (status.confirmedAgreement()) centered(page, "已开通自动续费", 16, Theme.SECONDARY, false);
        centered(page, "权益有效至 " + date(status.expiresAt), 14, Theme.SECONDARY, false);
        centered(page, "每笔算力的使用与到期情况，可返回算力页查看。", 13, Theme.SECONDARY, false);
        page.addView(new View(activity), new LinearLayout.LayoutParams(1, 0, 1));
        TextView done = button(page, "完成", SUCCESS);
        done.setTextColor(SUCCESS);
        GradientDrawable doneBackground = round(activity, Theme.BG, 9);
        doneBackground.setStroke(dp(activity, 1), SUCCESS);
        done.setBackground(doneBackground);
        done.setOnClickListener(v -> dialog.dismiss());
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.BG);
        scroll.addView(page);
        dialog.setContentView(scroll);
        dialog.show();
        DialogWindowDefaults.applyFullscreen(dialog.getWindow());
        SystemBarDefaults.applyLightEdgeToEdgeVisible(dialog.getWindow());
        return dialog;
    }

    private static IosDialog sheetWithFooter(Activity activity, String title, LinearLayout body, LinearLayout footer, int preferredHeight) {
        int availableDp = (int) (activity.getResources().getDisplayMetrics().heightPixels
                / activity.getResources().getDisplayMetrics().density) - 160;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        // With large text or a short landscape window, keep all controls reachable by scrolling.
        if (availableDp < 400 || activity.getResources().getConfiguration().fontScale >= 1.5f) {
            body.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        } else {
            View divider = new View(activity);
            divider.setBackgroundColor(Theme.BORDER_CHROME);
            content.addView(divider, new LinearLayout.LayoutParams(-1, dp(activity, 1)));
            content.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        }
        return IosDialog.showBottomSheetFixedContent(activity, title, content,
                Math.min(preferredHeight, Math.max(160, availableDp)), null, null, null, null, true, false);
    }

    public static void openProduct(Activity activity) {
        try {
            WechatProductActivity.open(activity);
        } catch (RuntimeException e) {
            SimpleToast.show(activity, "暂时无法打开介绍页面，请稍后再试。");
        }
    }

    private static void paymentMethod(LinearLayout body) {
        Activity activity = (Activity) body.getContext();
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        GradientDrawable background = round(activity, Theme.CARD, 9);
        background.setStroke(dp(activity, 1), Theme.BORDER_CHROME);
        row.setBackground(background);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_wechat);
        icon.setImageTintList(ColorStateList.valueOf(SUCCESS));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)));
        TextView name = new TextView(activity);
        name.setText("微信支付");
        name.setTextSize(16);
        name.setTextColor(Theme.INK);
        name.setPadding(dp(activity, 10), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView type = new TextView(activity);
        type.setText("自动续费");
        type.setTextSize(12);
        type.setTextColor(Theme.SECONDARY);
        row.addView(type);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, 16);
        params.bottomMargin = dp(activity, 6);
        body.addView(row, params);
    }

    private static LinearLayout column(Activity activity) {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(activity, 20), dp(activity, 6), dp(activity, 20), dp(activity, 16));
        return view;
    }

    private static TextView label(LinearLayout body, String value, int size, int color, boolean bold) {
        TextView text = new TextView(body.getContext());
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        text.setLineSpacing(dp(body.getContext(), 3), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(body.getContext(), 8);
        body.addView(text, params);
        return text;
    }

    private static void centered(LinearLayout body, String value, int size, int color, boolean bold) {
        label(body, value, size, color, bold).setGravity(Gravity.CENTER);
    }

    private static void section(LinearLayout body, String title, String message) {
        TextView heading = label(body, title, 16, Theme.INK, true);
        heading.setPadding(0, dp(body.getContext(), 10), 0, 0);
        label(body, message, 14, Theme.SECONDARY, false);
    }

    private static TextView link(LinearLayout body, String title, Runnable action) {
        TextView link = label(body, title, 14, PRIMARY, false);
        link.setMinHeight(dp(body.getContext(), 48));
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setOnClickListener(v -> action.run());
        return link;
    }

    private static TextView button(LinearLayout body, String title, int color) {
        TextView button = label(body, title, 16, 0xffffffff, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(body.getContext(), 52));
        button.setPadding(dp(body.getContext(), 12), dp(body.getContext(), 12), dp(body.getContext(), 12), dp(body.getContext(), 12));
        button.setBackground(round(body.getContext(), color, 9));
        button.setContentDescription(title);
        return button;
    }

    private static GradientDrawable round(android.content.Context context, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    private static int dp(android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static String number(double value) { return String.format(Locale.CHINA, "%.0f", value); }

    public static String date(long value) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA);
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        return formatter.format(new Date(value));
    }
}
