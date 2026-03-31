package com.example.macromod.data.items;

import com.example.macromod.data.blockpos.BaseBlockPos;
import com.example.macromod.data.blocks.BlockUtils;
import com.example.macromod.data.blocks.BlockWrapper;
import com.example.macromod.data.items.wrapper.ItemWrapper;
import com.example.macromod.minecraft.MinecraftAdapter;

public class ItemUtils {


    private static ItemLibrary itemLibrary;
    private static MinecraftAdapter minecraftAdapter;

    public static void initialize(MinecraftAdapter minecraftAdapter, ItemLibrary itemLibrary) {
        ItemUtils.itemLibrary = itemLibrary;
        ItemUtils.minecraftAdapter = minecraftAdapter;
    }


    public static ItemLibrary getItemLibrary() {
        return ItemUtils.itemLibrary;
    }


    /**
     * @param position the position of the block to break
     * @return the time (in ticks) it takes to break the block without a tool
     */
    public static float getBreakDuration(BaseBlockPos position) {
        final BlockWrapper blockWrapper = BlockUtils.getBlockProvider().getBlockAt(position);
        return minecraftAdapter.getBreakDuration(null, blockWrapper);
    }


    /**
     * @param item the used item
     * @param position  the position of the block to break
     * @return the time (in ticks) it takes to break the block with the given item
     */
    public static float getBreakDuration(ItemWrapper item, BaseBlockPos position) {
        final BlockWrapper blockWrapper = BlockUtils.getBlockProvider().getBlockAt(position);
        return minecraftAdapter.getBreakDuration(null, blockWrapper);
    }

    /**
     * @param item the used item
     * @param block  the block to break
     * @return the time (in ticks) it takes to break the block with the given item
     */
    public static float getBreakDuration(ItemWrapper item, BlockWrapper block) {
        return minecraftAdapter.getBreakDuration(null, block);
    }


}
