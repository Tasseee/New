package de.ancientreminder;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Erze/Blöcke, nach denen der Erz-Finder sucht. */
public enum Target {
    ANCIENT_DEBRIS("Ancient Debris", 0xC864FF, Items.ANCIENT_DEBRIS, true, Blocks.ANCIENT_DEBRIS),
    GILDED_BLACKSTONE("Gilded Blackstone", 0xFFD700, Items.GILDED_BLACKSTONE, false, Blocks.GILDED_BLACKSTONE),
    NETHER_GOLD("Nether Gold", 0xFFAA00, Items.GOLD_NUGGET, false, Blocks.NETHER_GOLD_ORE),
    NETHER_QUARTZ("Nether Quarz", 0xEEEEEE, Items.QUARTZ, false, Blocks.NETHER_QUARTZ_ORE),
    DIAMOND("Diamant", 0x55FFFF, Items.DIAMOND, true, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
    EMERALD("Smaragd", 0x55FF55, Items.EMERALD, false, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
    GOLD("Gold", 0xFFFF55, Items.RAW_GOLD, false, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE),
    IRON("Eisen", 0xD8AF93, Items.RAW_IRON, false, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);

    public final String label;
    public final int color;
    public final Item item;
    public final boolean defaultEnabled;
    public final Block[] blocks;

    Target(String label, int color, Item item, boolean defaultEnabled, Block... blocks) {
        this.label = label;
        this.color = color;
        this.item = item;
        this.defaultEnabled = defaultEnabled;
        this.blocks = blocks;
    }

    public Config.TargetCfg cfg() {
        return Config.I.targets.get(name());
    }
}
