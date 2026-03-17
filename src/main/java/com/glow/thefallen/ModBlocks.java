package com.glow.thefallen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, TheFallenMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, TheFallenMod.MODID);

    // The Glitched Block: unbreakable corruption block
    public static final DeferredHolder<Block, GlitchedBlock> GLITCHED_BLOCK =
            BLOCKS.register("glitched_block", GlitchedBlock::new);

    // Block Item (so it can exist in inventories/creative)
    public static final DeferredHolder<Item, BlockItem> GLITCHED_BLOCK_ITEM =
            ITEMS.register("glitched_block", () -> new BlockItem(GLITCHED_BLOCK.get(), new Item.Properties()));
}
