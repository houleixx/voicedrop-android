package com.baixingai.voicedrop.share;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public final class SharePayload {
    public Uri audio;
    public final List<Uri> images = new ArrayList<>();
    public Uri webUrl;
    public final List<Uri> docs = new ArrayList<>();
    public String text;
}
