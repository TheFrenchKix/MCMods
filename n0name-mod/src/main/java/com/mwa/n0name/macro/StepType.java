package com.mwa.n0name.macro;

public enum StepType {
    // Movement
    WALK("Walk", Category.MOVEMENT),

    // Teleport / Warp
    TELEPORT("Teleport", Category.MOVEMENT),
    WARP("Warp", Category.SPECIAL),

    // Interaction
    INTERACT("Interact", Category.INTERACTION),
    CLICK("Click", Category.INTERACTION),
    USE_ITEM("Use Item", Category.INTERACTION),
    SELECT_SLOT("Select Slot", Category.INTERACTION),

    // Farming
    MINE("Mine", Category.INTERACTION),
    HARVEST("Harvest", Category.INTERACTION),
    REPLANT("Replant", Category.INTERACTION),

    // Combat
    ATTACK("Attack", Category.COMBAT),
    TARGET_NEAREST("Target Nearest", Category.COMBAT),
    TARGET_TYPE("Target Type", Category.COMBAT),

    // Block/Menu
    BLOCK("Block", Category.INTERACTION),
    OPEN_MENU("Open Menu", Category.SPECIAL),
    SELL_ITEMS("Sell Items", Category.SPECIAL),
    BUY_ITEM("Buy Item", Category.SPECIAL),
    STORAGE_ACCESS("Storage Access", Category.SPECIAL),

    // Skyblock specials
    PET_SWAP("Pet Swap", Category.SPECIAL),
    ABILITY_USE("Ability Use", Category.SPECIAL),
    AOTE_WARP("AOTE Warp", Category.SPECIAL),
    FARM_ROW("Farm Row", Category.SPECIAL),

    // Flow control
    WAIT("Wait", Category.FLOW),
    CONDITION("Condition", Category.FLOW),
    LOOP("Loop", Category.FLOW),
    STOP("Stop", Category.FLOW);

    public enum Category {
        MOVEMENT(0xFF44FF44),    // green
        INTERACTION(0xFFFFDD44), // yellow
        COMBAT(0xFFFF4444),      // red
        SPECIAL(0xFFBB44FF),     // purple
        FLOW(0xFF4488FF);        // blue

        public final int color;
        Category(int color) { this.color = color; }
    }

    public final String label;
    public final Category category;

    StepType(String label, Category category) {
        this.label = label;
        this.category = category;
    }
}
