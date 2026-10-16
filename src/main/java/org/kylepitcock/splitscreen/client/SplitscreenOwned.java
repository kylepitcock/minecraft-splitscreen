package org.kylepitcock.splitscreen.client;


/** Implemented (via mixin) by client packet listeners to remember which local player owns them. */
public interface SplitscreenOwned {
    PlayerContext splitscreen$getContext();
}
