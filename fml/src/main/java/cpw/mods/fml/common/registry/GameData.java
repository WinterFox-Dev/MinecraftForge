/*
 * Forge Mod Loader
 * Copyright (c) 2012-2013 cpw.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     cpw - implementation
 */

package cpw.mods.fml.common.registry;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.logging.log4j.Level;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Joiner.MapJoiner;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.io.Files;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public class GameData {
    private static Table<String, String, ItemStack> customItemStacks = HashBasedTable.create();

    public static final FMLControlledNamespacedRegistry<Block> blockRegistry = new FMLControlledNamespacedRegistry<Block>("air", 4095, 0, Block.class,'\u0001');
    public static final FMLControlledNamespacedRegistry<Item> itemRegistry = new FMLControlledNamespacedRegistry<Item>(null, 32000, 4096, Item.class,'\u0002');

    public static Map<String,Integer> buildItemDataList()
    {
        Map<String,Integer> idMapping = Maps.newHashMap();
        blockRegistry.serializeInto(idMapping);
        itemRegistry.serializeInto(idMapping);
        return idMapping;
    }

    static Item findItem(String modId, String name)
    {
        return (Item) itemRegistry.func_82594_a(modId + ":" + name);
    }

    static Block findBlock(String modId, String name)
    {
        return (Block) blockRegistry.func_82594_a(modId + ":" + name);
    }

    static ItemStack findItemStack(String modId, String name)
    {
        ItemStack is = customItemStacks.get(modId, name);
        if (is == null)
        {
            Item i = findItem(modId, name);
            if (i != null)
            {
                is = new ItemStack(i, 0 ,0);
            }
        }
        if (is == null)
        {
            Block b = findBlock(modId, name);
            if (b != null)
            {
                is = new ItemStack(b, 0, Short.MAX_VALUE);
            }
        }
        return is;
    }

    static void registerCustomItemStack(String name, ItemStack itemStack)
    {
        customItemStacks.put(Loader.instance().activeModContainer().getModId(), name, itemStack);
    }

    public static void dumpRegistry(File minecraftDir)
    {
        if (customItemStacks == null)
        {
            return;
        }
        if (Boolean.valueOf(System.getProperty("fml.dumpRegistry", "false")).booleanValue())
        {
            ImmutableListMultimap.Builder<String, String> builder = ImmutableListMultimap.builder();
            for (String modId : customItemStacks.rowKeySet())
            {
                builder.putAll(modId, customItemStacks.row(modId).keySet());
            }

            File f = new File(minecraftDir, "itemStackRegistry.csv");
            MapJoiner mapJoiner = Joiner.on("\n").withKeyValueSeparator(",");
            try
            {
                Files.write(mapJoiner.join(builder.build().entries()), f, Charsets.UTF_8);
                FMLLog.log(Level.INFO, "Dumped item registry data to %s", f.getAbsolutePath());
            }
            catch (IOException e)
            {
                FMLLog.log(Level.ERROR, e, "Failed to write registry data to %s", f.getAbsolutePath());
            }
        }
    }

    static UniqueIdentifier getUniqueName(Block block)
    {
        if (block == null) return null;
        String name = blockRegistry.func_148750_c(block);
        UniqueIdentifier ui = new UniqueIdentifier(name);
        if (customItemStacks.contains(ui.modId, ui.name))
        {
            return null;
        }

        return ui;
    }

    static UniqueIdentifier getUniqueName(Item item)
    {
        if (item == null) return null;
        String name = itemRegistry.func_148750_c(item);
        UniqueIdentifier ui = new UniqueIdentifier(name);
        if (customItemStacks.contains(ui.modId, ui.name))
        {
            return null;
        }

        return ui;
    }

    private static Map<UniqueIdentifier, ModContainer> customOwners = Maps.newHashMap();

    static void registerBlockAndItem(ItemBlock item, Block block, String name, String modId)
    {
        ModContainer mc = Loader.instance().activeModContainer();
        if (modId != null)
        {
            customOwners.put(new UniqueIdentifier(modId, name), mc);
        }
        int blockId = blockRegistry.add(0, name, block);
        int itemId = itemRegistry.add(blockId, name, item);
        if (itemId != blockId)
        {
            throw new RuntimeException();
        }

    }
    static void registerItem(Item item, String name, String modId)
    {
        ModContainer mc = Loader.instance().activeModContainer();
        if (modId != null)
        {
            customOwners.put(new UniqueIdentifier(modId, name), mc);
        }
        if (item instanceof ItemBlock)
        {
            throw new RuntimeException("Cannot register an itemblock separately from it's block");
        }
        int itemId = itemRegistry.add(0, name, item);
        blockRegistry.useSlot(itemId);
    }

    static void registerBlock(Block block, String name, String modId)
    {
        ModContainer mc = Loader.instance().activeModContainer();
        if (modId != null)
        {
            customOwners.put(new UniqueIdentifier(modId, name), mc);
        }
        int blockId = blockRegistry.add(0, name, block);
        itemRegistry.useSlot(blockId);
    }

    public static ModContainer findModOwner(String string)
    {
        UniqueIdentifier ui = new UniqueIdentifier(string);
        if (customOwners.containsKey(ui))
        {
            return customOwners.get(ui);
        }
        return Loader.instance().getIndexedModList().get(ui.modId);
    }


    public static void fixupRegistries()
    {
        for (Integer id : blockRegistry.usedIds())
        {
            itemRegistry.useSlot(id);
        }

        for (Integer id : itemRegistry.usedIds())
        {
            blockRegistry.useSlot(id);
        }
    }

    public static void injectWorldIDMap(Map<String, Integer> dataList)
    {
        Map<String, Integer[]> remaps = Maps.newHashMap();
        blockRegistry.beginIdSwap();
        itemRegistry.beginIdSwap();
        for (Entry<String, Integer> entry : dataList.entrySet())
        {
            String itemName = entry.getKey();
            char discriminator = itemName.charAt(0);
            itemName = itemName.substring(1);
            Integer newId = entry.getValue();
            int currId;
            boolean isBlock = discriminator == '\u0001';
            if (isBlock)
            {
                currId = blockRegistry.getId(itemName);
            }
            else
            {
                currId = itemRegistry.getId(itemName);
            }

            if (currId != newId)
            {
                FMLLog.info("Found %s id mismatch %s : %d %d", isBlock ? "block" : "item", itemName, currId, newId);
                remaps.put(itemName, new Integer[] { currId, newId });
            }

            if (isBlock)
            {
                blockRegistry.reassignMapping(itemName, newId);
            }
            else
            {
                itemRegistry.reassignMapping(itemName, newId);
            }
        }

        blockRegistry.completeIdSwap();
        itemRegistry.completeIdSwap();
        Loader.instance().fireRemapEvent(remaps);
    }
}
