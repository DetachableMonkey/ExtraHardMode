package com.extrahardmode.features;

import com.extrahardmode.ExtraHardMode;
import com.extrahardmode.config.RootConfig;
import com.extrahardmode.config.RootNode;
import com.extrahardmode.config.messages.MessageConfig;
import com.extrahardmode.config.messages.MessageNode;
import com.extrahardmode.module.BlockModule;
import com.extrahardmode.module.MessagingModule;
import com.extrahardmode.module.UtilityModule;
import com.extrahardmode.service.PermissionNode;
import com.extrahardmode.task.EvaporateWaterTask;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SheepRegrowWoolEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class AntiFarming implements Listener
{
    ExtraHardMode plugin;
    RootConfig CFG;
    UtilityModule utils;

    public AntiFarming (ExtraHardMode plugin)
    {
        this.plugin = plugin;
        CFG = plugin.getModuleForClass(RootConfig.class);
        utils = plugin.getModuleForClass(UtilityModule.class);
    }

    /**
     * when a player interacts with the world
     *
     * @param event - Event that occurred.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    void onPlayerInteract(PlayerInteractEvent event)
    {
        Player player = event.getPlayer();
        World world = event.getPlayer().getWorld();
        Action action = event.getAction();

        final boolean noBonemealOnMushrooms = CFG.getBoolean(RootNode.NO_BONEMEAL_ON_MUSHROOMS, world.getName())
                                        &&! player.hasPermission(PermissionNode.BYPASS.getNode());
        final boolean weakFoodCrops = CFG.getBoolean(RootNode.WEAK_FOOD_CROPS, world.getName())
                                &&! player.hasPermission(PermissionNode.BYPASS.getNode());

        // FEATURE: bonemeal doesn't work on mushrooms
        if (noBonemealOnMushrooms && action == Action.RIGHT_CLICK_BLOCK)
        {
            Block block = event.getClickedBlock();
            if (block.getType() == Material.RED_MUSHROOM || block.getType() == Material.BROWN_MUSHROOM)
            {
                // what's the player holding?
                Material materialInHand = player.getItemInHand().getType();

                // if bonemeal, cancel the event
                if (materialInHand == Material.INK_SACK) // bukkit labels bonemeal as ink sack
                {
                    event.setCancelled(true);
                }
            }
        }

        // FEATURE: seed reduction. some plants die even when a player uses bonemeal.
        if (weakFoodCrops && action.equals(Action.RIGHT_CLICK_BLOCK))
        {
            Block block = event.getClickedBlock();
            if (utils.isPlant(block.getType()))
            {
                Material materialInHand = player.getItemInHand().getType();
                if (materialInHand == Material.INK_SACK && plugin.getModuleForClass(BlockModule.class).plantDies(block, Byte.MAX_VALUE))
                {
                    event.setCancelled(true);
                    block.setType(Material.LONG_GRASS); // dead shrub
                }
            }
        }
    }

    /**
     * When a player breaks a block...
     *
     * @param breakEvent - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent breakEvent)
    {
        Player player = breakEvent.getPlayer();
        Block block = breakEvent.getBlock();
        World world = block.getWorld();

        final boolean noFarmingNetherWart = CFG.getBoolean(RootNode.NO_FARMING_NETHER_WART, world.getName());
        final boolean playerHasBypass = player != null ? player.hasPermission(PermissionNode.BYPASS.getNode()) : true; //true = has bypass won't run if no player

        // FEATURE: no nether wart farming (always drops exactly 1 nether wart when broken)
        if (!playerHasBypass && noFarmingNetherWart)
        {
            if (block.getType() == Material.NETHER_WARTS)
            {
                block.getDrops().clear();
                block.getDrops().add(new ItemStack(Material.NETHER_STALK));
            }
        }
    }

    /**
     * When a player places a block...
     * no farming nether wart
     * @param placeEvent
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent placeEvent)
    {
        Player player = placeEvent.getPlayer();
        Block block = placeEvent.getBlock();
        World world = block.getWorld();

        final boolean noFarmingNetherWart = CFG.getBoolean(RootNode.NO_FARMING_NETHER_WART, world.getName());
        final boolean playerHasBypass = player != null ? player.hasPermission(PermissionNode.BYPASS.getNode()) : true; //true = has bypass won't run if no player

        // FEATURE: no farming/placing nether wart
        if (!playerHasBypass && noFarmingNetherWart && block.getType() == Material.NETHER_WARTS)
        {
            placeEvent.setCancelled(true);
            return;
        }
    }

    /**
     * When a block grows...
     * fewer seeds = shrinking crops. when a plant grows to its full size, it may be replaced by a dead shrub
     *
     * @param event - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockGrow(BlockGrowEvent event)
    {
        World world = event.getBlock().getWorld();

        final boolean weakCropsEnabled = CFG.getBoolean(RootNode.WEAK_FOOD_CROPS, world.getName());

        // FEATURE:
        if (weakCropsEnabled && plugin.getModuleForClass(BlockModule.class).plantDies(event.getBlock(), event.getNewState().getData().getData()))
        {
            event.setCancelled(true);
            event.getBlock().setType(Material.LONG_GRASS); // dead shrub
        }
    }

    /**
     * when a tree or mushroom grows...
     * no big plant growth in deserts
     *
     * @param event - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onStructureGrow(StructureGrowEvent event)
    {
        World world = event.getWorld();
        Block block = event.getLocation().getBlock();

        boolean aridDesertsEnabled = CFG.getBoolean(RootNode.ARID_DESSERTS, world.getName());


        if (aridDesertsEnabled)
        {
            Biome biome = block.getBiome();
            if (biome == Biome.DESERT || biome == Biome.DESERT_HILLS)
            {
                event.setCancelled(true);
            }
        }
    }

    /**
     * when a dispenser dispenses...
     *
     * @param event - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    void onBlockDispense(BlockDispenseEvent event)
    {
        World world = event.getBlock().getWorld();

        final boolean dontMoveWaterEnabled = CFG.getBoolean(RootNode.DONT_MOVE_WATER_SOURCE_BLOCKS, world.getName());

        // FEATURE: can't move water source blocks
        if (dontMoveWaterEnabled)
        {
            // only care about water
            if (event.getItem().getType() == Material.WATER_BUCKET)
            {
                // plan to evaporate the water next tick
                Block block;
                Vector velocity = event.getVelocity();
                if (velocity.getX() > 0)
                {
                    block = event.getBlock().getLocation().add(1, 0, 0).getBlock();
                }
                else if (velocity.getX() < 0)
                {
                    block = event.getBlock().getLocation().add(-1, 0, 0).getBlock();
                }
                else if (velocity.getZ() > 0)
                {
                    block = event.getBlock().getLocation().add(0, 0, 1).getBlock();
                }
                else
                {
                    block = event.getBlock().getLocation().add(0, 0, -1).getBlock();
                }

                EvaporateWaterTask task = new EvaporateWaterTask(block);
                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, 1L);
            }
        }
    }

    /**
     * when a sheep regrows its wool...
     *
     * @param event - Event that occurred.
     */
    @EventHandler
    public void onSheepRegrowWool(SheepRegrowWoolEvent event)
    {
        World world = event.getEntity().getWorld();

        boolean sheepRegrowWhiteEnabled = CFG.getBoolean(RootNode.SHEEP_REGROW_WHITE_WOOL, world.getName());

        // FEATURE: sheep are all white, and may be dyed only temporarily
        if (sheepRegrowWhiteEnabled)
        {
            Sheep sheep = event.getEntity();
            if (sheep.isSheared())
                sheep.setColor(DyeColor.WHITE);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntitySpawn(CreatureSpawnEvent event)
    {
        LivingEntity entity = event.getEntity();
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        EntityType entityType = entity.getType();
        World world = event.getLocation().getWorld();

        final boolean sheepRegrowWhiteEnabled = CFG.getBoolean(RootNode.SHEEP_REGROW_WHITE_WOOL, world.getName());

        //Breed Sheep spawn white
        if (sheepRegrowWhiteEnabled && entityType == EntityType.SHEEP)
        {
            Sheep sheep = (Sheep) entity;
            if (reason.equals(CreatureSpawnEvent.SpawnReason.BREEDING))
            {
                sheep.setColor(DyeColor.WHITE);
                return;
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event)
    {
        LivingEntity entity = event.getEntity();
        World world = entity.getWorld();

        final boolean animalExpNerfEnabled = CFG.getBoolean(RootNode.ANIMAL_EXP_NERF, world.getName());

        // FEATURE: animals don't drop experience (because they're easy to "farm")
        if (animalExpNerfEnabled && entity instanceof Animals)
        {
            event.setDroppedExp(0);
        }
    }

    /**
     * when a player crafts something...
     *
     * @param event - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onItemCrafted(CraftItemEvent event)
    {
        World world = null;
        Material result = event.getRecipe().getResult().getType();
        InventoryHolder human = event.getInventory().getHolder();
        Player player = null;
        if (human instanceof Player)
        {
            player = (Player)human;
            world = player.getWorld();
        }

        final boolean cantCraftMelons = world != null && CFG.getBoolean(RootNode.CANT_CRAFT_MELONSEEDS, world.getName());
        final boolean playerHasBypass = player != null ? player.hasPermission(PermissionNode.BYPASS.getNode()) : true; //true = has bypass won't run if no player

        MessageConfig messages = plugin.getModuleForClass(MessageConfig.class);


        if (!playerHasBypass && cantCraftMelons)
        {
            // FEATURE: no crafting melon seeds
            if (cantCraftMelons && (result == Material.MELON_SEEDS || result == Material.PUMPKIN_SEEDS))
            {
                event.setCancelled(true);
                plugin.getModuleForClass(MessagingModule.class).sendMessage(player, MessageNode.NO_CRAFTING_MELON_SEEDS);
                return;
            }
        }
    }

    /**
     * when a player empties a bucket...
     *
     * @param event - Event that occurred.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerEmptyBucket(PlayerBucketEmptyEvent event)
    {
        Player player = event.getPlayer();
        World world = player.getWorld();

        final boolean dontMoveWaterEnabled = CFG.getBoolean(RootNode.DONT_MOVE_WATER_SOURCE_BLOCKS, world.getName());
        final boolean playerHasBypass = player != null ? player.hasPermission(PermissionNode.BYPASS.getNode())
                                        || player.getGameMode().equals(GameMode.CREATIVE) : true; //true = has bypass won't run if no player

        // FEATURE: can't move water source blocks
        if (!playerHasBypass && dontMoveWaterEnabled && player.getItemInHand().getType().equals(Material.WATER_BUCKET))
        {
            // plan to change this block into a non-source block on the next tick
            Block block = event.getBlockClicked().getRelative(event.getBlockFace());
            EvaporateWaterTask task = new EvaporateWaterTask(block);
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, 10L);
        }
    }
}
