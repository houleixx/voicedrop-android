package com.baixingai.voicedrop.ui;

import android.widget.ImageView;

import com.baixingai.voicedrop.R;

public final class AliIconFont {
    public static final int BACK = R.drawable.ic_back;
    public static final int CAMERA = R.drawable.ic_camera;
    public static final int DOC = R.drawable.ic_doc;
    public static final int FLAG = R.drawable.ic_flag;
    public static final int HAND = R.drawable.ic_hand;
    public static final int HEART = R.drawable.ic_heart;
    public static final int HEART_FILLED = R.drawable.ic_heart_filled;
    public static final int IMAGE = R.drawable.ic_image;
    public static final int IMAGE_UPLOAD_FLAT = R.drawable.ic_image_upload_flat;
    public static final int MIC = R.drawable.ic_mic;
    public static final int MORE = R.drawable.ic_more;
    public static final int PAPERPLANE = R.drawable.ic_paperplane;
    public static final int PEOPLE = R.drawable.ic_people;
    public static final int PLAY = R.drawable.ic_play;
    public static final int REDO = R.drawable.ic_redo;
    public static final int SETTINGS = R.drawable.ic_settings;
    public static final int SHARE_UP = R.drawable.ic_share_up;
    public static final int TRASH = R.drawable.ic_trash;
    public static final int UNDO = R.drawable.ic_undo;

    private AliIconFont() {
    }

    public static void apply(ImageView view, int iconResId, int color) {
        view.setImageResource(iconResId);
        view.setColorFilter(color);
    }
}
