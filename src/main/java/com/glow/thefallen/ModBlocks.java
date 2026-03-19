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

    public static final DeferredHolder<Item, BlockItem> GLITCHED_BLOCK_ITEM =
            ITEMS.register("glitched_block", () -> new BlockItem(GLITCHED_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> EERIE_COBBLESTONE =
            BLOCKS.register("eerie_cobblestone", () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(1.5F, 6.0F).requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, BlockItem> EERIE_COBBLESTONE_ITEM =
            ITEMS.register("eerie_cobblestone", () -> new BlockItem(EERIE_COBBLESTONE.get(), new Item.Properties()));

    // Eerie Torch: a dim, soul-fire torch placed by the Observer
    public static final DeferredHolder<Block, EerieTorchBlock> EERIE_TORCH =
            BLOCKS.register("eerie_torch", EerieTorchBlock::new);

    public static final DeferredHolder<Block, EerieWallTorchBlock> EERIE_WALL_TORCH =
            BLOCKS.register("eerie_wall_torch", EerieWallTorchBlock::new);

    public static final DeferredHolder<Item, BlockItem> EERIE_TORCH_ITEM =
            ITEMS.register("eerie_torch", () -> new BlockItem(EERIE_TORCH.get(), new Item.Properties()));
}
