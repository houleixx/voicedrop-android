package com.baixingai.voicedrop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BookWritingIconTest {
    @Test
    public void shelfCardUsesBooksVectorIcon() {
        assertEquals(R.drawable.ic_about_books_vertical, BookWritingActivity.SHELF_ICON_RES_ID);
    }
}
