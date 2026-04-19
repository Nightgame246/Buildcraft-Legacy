package com.thepigcat.buildcraft.content.enums;

public enum EnumAssemblyRecipeState {
    POSSIBLE,            // items in inv match, not saved by player
    SAVED,               // player saved it, but items missing
    SAVED_ENOUGH,        // saved + items present, waiting for active slot
    SAVED_ENOUGH_ACTIVE  // currently receiving power / crafting
}
