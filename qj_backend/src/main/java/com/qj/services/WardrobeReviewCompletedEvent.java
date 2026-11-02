package com.qj.services;

/** Signals that a reviewed wardrobe item produced a reusable positive or negative sample. */
public final class WardrobeReviewCompletedEvent {

    private final Long itemId;

    public WardrobeReviewCompletedEvent(Long itemId) {
        this.itemId = itemId;
    }

    public Long getItemId() {
        return itemId;
    }
}
