package com.vijaypurohit.movietickets.shared.pagination;

public interface CursorCodec {

    String encode(PageCursor cursor);

    PageCursor decode(String encodedCursor, CursorResource expectedResource);
}
