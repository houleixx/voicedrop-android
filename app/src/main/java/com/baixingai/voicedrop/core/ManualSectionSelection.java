package com.baixingai.voicedrop.core;

public final class ManualSectionSelection {
    private int selectedSection;
    private int pendingTappedSection = -1;

    public int onSectionTapped(int index) {
        if (index != selectedSection) pendingTappedSection = index;
        selectedSection = index;
        return selectedSection;
    }

    public int onSectionReported(int index) {
        if (pendingTappedSection >= 0) {
            if (index == pendingTappedSection) pendingTappedSection = -1;
            return selectedSection;
        }
        selectedSection = index;
        return selectedSection;
    }

    public int selectedSection() {
        return selectedSection;
    }

    public void releaseTappedSection() {
        pendingTappedSection = -1;
    }
}
