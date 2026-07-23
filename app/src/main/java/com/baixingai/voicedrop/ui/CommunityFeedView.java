package com.baixingai.voicedrop.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.baixingai.voicedrop.R;
import com.baixingai.voicedrop.data.CommunityStore;
import com.baixingai.voicedrop.data.PhotoService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android-native counterpart of iOS CommunityFeedView: tabs plus a two-column masonry feed. */
public final class CommunityFeedView extends LinearLayout {
    private static final ExecutorService IMAGES = Executors.newFixedThreadPool(4);

    public interface Listener {
        void onSelect(CommunityStore.Post post);
        void onUnshare(CommunityStore.Post post);
        void onTabChanged(CommunityFeedPresentation.Tab tab);
    }

    private static final int[][] PALETTES = {
            {0xfffbefe0, 0xfff6e3ce},
            {0xffede7dc, 0xffe2dacb},
            {0xffe7ede3, 0xffd6e0ce}
    };

    private CommunityStore.Feed feed;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FeedAdapter adapter;
    private final RecyclerView grid;
    private final List<TextView> tabViews = new ArrayList<>();
    private CommunityFeedPresentation.Tab selected = CommunityFeedPresentation.Tab.RECOMMENDED;

    public CommunityFeedView(Context context, CommunityStore.Feed feed, Listener listener,
                             CommunityFeedPresentation.Tab initialTab) {
        super(context);
        this.feed = feed == null ? CommunityStore.Feed.empty() : feed;
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(0xfff3efe7);

        addView(buildTabs(), new LayoutParams(-1, dp(38)));
        grid = new RecyclerView(context);
        StaggeredGridLayoutManager layout = new StaggeredGridLayoutManager(2,
                StaggeredGridLayoutManager.VERTICAL);
        layout.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        grid.setLayoutManager(layout);
        grid.setClipToPadding(false);
        SystemBarDefaults.applyBottomInsets(grid, dp(8), 0, dp(8), dp(24));
        grid.setItemAnimator(null);
        adapter = new FeedAdapter();
        grid.setAdapter(adapter);
        addView(grid, new LayoutParams(-1, 0, 1));
        select(initialTab == null ? CommunityFeedPresentation.Tab.RECOMMENDED : initialTab);
    }

    /** The masonry list moves during pull-to-refresh; the filter tabs above it stay fixed. */
    public View refreshTarget() {
        return grid;
    }

    /** Places the refresh spinner immediately below the fixed filter tabs. */
    public int refreshSpinnerOffset() {
        return dp(38);
    }

    /** Updates the existing masonry list without recreating the pager or losing scroll state. */
    public void updateFeed(CommunityStore.Feed next) {
        feed = next == null ? CommunityStore.Feed.empty() : next;
        adapter.setPosts(CommunityFeedPresentation.posts(feed, selected));
    }

    private View buildTabs() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.addView(tab("推荐", CommunityFeedPresentation.Tab.RECOMMENDED));
        row.addView(tab("最新", CommunityFeedPresentation.Tab.LATEST));
        row.addView(tab("回应", CommunityFeedPresentation.Tab.REPLIES));
        return row;
    }

    private TextView tab(String label, CommunityFeedPresentation.Tab tab) {
        TextView view = text(label, 15, Theme.SECONDARY, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 0, dp(18), 0);
        view.setTag(tab);
        view.setOnClickListener(v -> select(tab));
        tabViews.add(view);
        return view;
    }

    private void select(CommunityFeedPresentation.Tab tab) {
        selected = tab;
        for (TextView view : tabViews) {
            boolean active = view.getTag() == selected;
            view.setTextColor(active ? Theme.INK : Theme.SECONDARY);
            view.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        }
        adapter.setPosts(CommunityFeedPresentation.posts(feed, selected));
        listener.onTabChanged(selected);
    }

    private final class FeedAdapter extends RecyclerView.Adapter<CardHolder> {
        private final List<CommunityStore.Post> posts = new ArrayList<>();

        void setPosts(List<CommunityStore.Post> next) {
            posts.clear();
            posts.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull @Override public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout slot = new LinearLayout(parent.getContext());
            slot.setOrientation(VERTICAL);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.setMargins(dp(4), dp(4), dp(4), dp(5));
            slot.setLayoutParams(lp);
            return new CardHolder(slot);
        }

        @Override public void onBindViewHolder(@NonNull CardHolder holder, int position) {
            CommunityStore.Post post = posts.get(position);
            holder.slot.removeAllViews();
            holder.slot.addView(card(post), new LayoutParams(-1, -2));
        }

        @Override public int getItemCount() { return posts.size(); }
    }

    private static final class CardHolder extends RecyclerView.ViewHolder {
        final LinearLayout slot;
        CardHolder(LinearLayout slot) { super(slot); this.slot = slot; }
    }

    private View card(CommunityStore.Post post) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setBackground(cardBackground(post.coverPhotoKey == null || post.coverPhotoKey.isEmpty(), post.shareId));
        card.setClipToOutline(true);

        if (post.coverPhotoKey != null && !post.coverPhotoKey.isEmpty()) addPhoto(card, post);
        addBody(card, post);
        card.setContentDescription((post.title == null || post.title.isEmpty() ? "无题" : post.title)
                + "，作者" + author(post));
        card.setOnClickListener(v -> listener.onSelect(post));
        card.setOnLongClickListener(v -> {
            if (!post.mine) return false;
            PopupMenu menu = new PopupMenu(getContext(), v);
            menu.getMenu().add("取消分享");
            menu.setOnMenuItemClickListener(item -> {
                listener.onUnshare(post);
                return true;
            });
            menu.show();
            return true;
        });
        return card;
    }

    private void addPhoto(LinearLayout card, CommunityStore.Post post) {
        ImageView image = new ImageView(getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Theme.CARD);
        int columnWidth = (getResources().getDisplayMetrics().widthPixels - dp(33)) / 2;
        card.addView(image, new LayoutParams(-1, columnWidth));
        String key = post.coverPhotoKey;
        image.setTag(key);
        IMAGES.execute(() -> {
            Bitmap bitmap = null;
            try { bitmap = PhotoService.thumbnail(key); } catch (Exception ignored) {}
            Bitmap loaded = bitmap;
            main.post(() -> {
                if (!key.equals(image.getTag())) return;
                if (loaded == null) {
                    image.setVisibility(GONE);
                    card.setBackground(cardBackground(true, post.shareId));
                    return;
                }
                image.setImageBitmap(loaded);
                int height = Math.max(dp(90), Math.min(dp(330),
                        Math.round(columnWidth * loaded.getHeight() / (float) Math.max(1, loaded.getWidth()))));
                image.setLayoutParams(new LayoutParams(-1, height));
            });
        });
    }

    private void addBody(LinearLayout card, CommunityStore.Post post) {
        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(VERTICAL);
        body.setPadding(dp(12), dp(11), dp(12), dp(12));
        if (post.isPrompt()) {
            TextView promptBadge = text("提示词", 11, 0xff6f5529, Typeface.BOLD);
            promptBadge.setGravity(Gravity.CENTER);
            promptBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
            GradientDrawable promptBackground = new GradientDrawable();
            promptBackground.setColor(0xfff4dfac);
            promptBackground.setCornerRadius(dp(10));
            promptBadge.setBackground(promptBackground);
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
            badgeLp.setMargins(0, 0, 0, dp(7));
            body.addView(promptBadge, badgeLp);
        }
        if (post.replyTo != null && !post.replyTo.isEmpty()) {
            body.addView(replyBadge(), new LayoutParams(-2, -2));
        }

        TextView title = text(post.title == null || post.title.isEmpty() ? "(无题)" : post.title,
                post.coverPhotoKey == null || post.coverPhotoKey.isEmpty() ? 16 : 15,
                Theme.INK, Typeface.NORMAL);
        title.setMaxLines(post.coverPhotoKey == null || post.coverPhotoKey.isEmpty() ? 3 : 2);
        title.setPadding(0, post.replyTo == null || post.replyTo.isEmpty() ? 0 : dp(7), 0, 0);
        body.addView(title, new LayoutParams(-1, -2));

        if (post.preview != null && !post.preview.isEmpty()
                && (post.coverPhotoKey == null || post.coverPhotoKey.isEmpty())) {
            TextView preview = text(post.preview, 13, 0xff8a7b63, Typeface.NORMAL);
            preview.setMaxLines(2);
            preview.setPadding(0, dp(8), 0, 0);
            body.addView(preview, new LayoutParams(-1, -2));
        }

        LinearLayout meta = new LinearLayout(getContext());
        meta.setOrientation(HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0, dp(10), 0, 0);
        TextView avatar = text(author(post).substring(0, 1), 10, 0xffffffff, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBackground = new GradientDrawable();
        int[] avatarColors = {0xffd8a25b, 0xff8a9a88, 0xffb5794c, 0xff7a6e9a, 0xff5e8a6a, 0xffc98a2e};
        int avatarIndex = (author(post).hashCode() & 0x7fffffff) % avatarColors.length;
        avatarBackground.setColor(avatarColors[avatarIndex]);
        avatarBackground.setShape(GradientDrawable.OVAL);
        avatar.setBackground(avatarBackground);
        meta.addView(avatar, new LayoutParams(dp(20), dp(20)));
        TextView author = text(author(post), 12, Theme.SECONDARY, Typeface.NORMAL);
        author.setSingleLine(true);
        author.setPadding(dp(6), 0, dp(4), 0);
        meta.addView(author, new LayoutParams(0, -2, 1));
        LinearLayout likes = new LinearLayout(getContext());
        likes.setOrientation(HORIZONTAL);
        likes.setGravity(Gravity.CENTER_VERTICAL);
        ImageView heart = new ImageView(getContext());
        heart.setImageResource(R.drawable.ic_community_heart);
        likes.addView(heart, new LayoutParams(dp(12), dp(12)));
        TextView likeCount = text(String.valueOf(feed.likeCount(post.shareId)),
                12, Theme.ACCENT, Typeface.NORMAL);
        likeCount.setPadding(dp(3), 0, 0, 0);
        likes.addView(likeCount, new LayoutParams(-2, -2));
        meta.addView(likes, new LayoutParams(-2, -2));
        int replies = feed.replyCount(post.shareId);
        if (replies > 0) {
            LinearLayout replyMeta = new LinearLayout(getContext());
            replyMeta.setOrientation(HORIZONTAL);
            replyMeta.setGravity(Gravity.CENTER_VERTICAL);
            replyMeta.setPadding(dp(6), 0, 0, 0);
            ImageView replyIcon = new ImageView(getContext());
            replyIcon.setImageResource(R.drawable.ic_comment);
            replyMeta.addView(replyIcon, new LayoutParams(dp(12), dp(12)));
            TextView replyCount = text(String.valueOf(replies),
                    12, Theme.SECONDARY, Typeface.NORMAL);
            replyCount.setPadding(dp(3), 0, 0, 0);
            replyMeta.addView(replyCount, new LayoutParams(-2, -2));
            meta.addView(replyMeta, new LayoutParams(-2, -2));
        }
        body.addView(meta, new LayoutParams(-1, -2));
        card.addView(body, new LayoutParams(-1, -2));
    }

    private View replyBadge() {
        LinearLayout badge = new LinearLayout(getContext());
        badge.setOrientation(HORIZONTAL);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), dp(2), dp(8), dp(2));
        ImageView replyBadgeIcon = new ImageView(getContext());
        replyBadgeIcon.setImageResource(R.drawable.ic_community_reply);
        replyBadgeIcon.setColorFilter(Theme.ACCENT);
        badge.addView(replyBadgeIcon, new LayoutParams(dp(11), dp(11)));
        TextView label = text("回应", 11, Theme.ACCENT, Typeface.NORMAL);
        label.setPadding(dp(4), 0, 0, 0);
        badge.addView(label, new LayoutParams(-2, -2));
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setColor(Theme.ACCENT_SOFT);
        badgeBackground.setCornerRadius(dp(99));
        badge.setBackground(badgeBackground);
        return badge;
    }

    private GradientDrawable cardBackground(boolean textCard, String shareId) {
        GradientDrawable drawable;
        if (textCard) {
            int[] palette = PALETTES[CommunityFeedPresentation.paletteIndex(shareId)];
            drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, palette);
        } else {
            drawable = new GradientDrawable();
            drawable.setColor(Theme.CARD);
        }
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private String author(CommunityStore.Post post) {
        return post.author == null || post.author.isEmpty() ? "匿名" : post.author;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(false);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
