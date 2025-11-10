// src/main/java/net/greenfieldmc/core/advancedbuild/handlers/IronDoorsInteraction.java
package net.greenfieldmc.core.advancedbuild.handlers;

import net.greenfieldmc.core.advancedbuild.InteractPredicate;
import net.greenfieldmc.core.advancedbuild.InteractionHandler;
import net.greenfieldmc.core.shared.services.ICoreProtectService;
import net.greenfieldmc.core.shared.services.IWorldEditService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IronDoorsInteraction extends InteractionHandler {

    private final Map<UUID, Boolean> sessions = new HashMap<>();

    public IronDoorsInteraction(IWorldEditService worldEditService, ICoreProtectService coreProtectService) {
    // update constructor predicate to also allow when holding the item being placed
        super(worldEditService, coreProtectService, (InteractPredicate) (event) -> {
            Block block = event.getClickedBlock();
            if (block != null && isIronDoorOrTrapdoor(block.getType())) return true;
            if (event.getItem() != null && isIronDoorOrTrapdoor(event.getItem().getType())) return true;
            return false;
        }, Material.IRON_DOOR);
    }

    @Override
    public TextComponent getInteractionDescription() {
        return Component.text("Shift right-click with empty hand to toggle iron door/trapdoor open state. Shift-place to use saved state.").color(NamedTextColor.GRAY);
    }

    @Override
    public TextComponent getInteractionUsage() {
        return Component.text("Shift right-click to toggle open. Shift-place to use saved open state.").color(NamedTextColor.GRAY);
    }

    @Override
    public void onRightClickBlock(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;

        // in onRightClickBlock, derive type from clicked block or from the held item when placing
        Material type = (event.getItem() != null && isIronDoorOrTrapdoor(event.getItem().getType()))
                ? event.getItem().getType()
                : block.getType();
        boolean isEmptyHand = event.getPlayer().getInventory().getItemInMainHand().getType() == Material.AIR;
        UUID uuid = event.getPlayer().getUniqueId();
        Player player = event.getPlayer();


        // Iron Door logic
        if (type == Material.IRON_DOOR && isEmptyHand && player.isSneaking()) {
            if (block.getBlockData() instanceof Door door) {
                boolean newOpen = !door.isOpen();
                door.setOpen(newOpen);
                block.setBlockData(door, false);

                // Update the other half
                Block otherHalf = (door.getHalf() == Door.Half.TOP)
                        ? block.getRelative(BlockFace.DOWN)
                        : block.getRelative(BlockFace.UP);
                if (otherHalf.getType() == Material.IRON_DOOR && otherHalf.getBlockData() instanceof Door otherDoor) {
                    otherDoor.setOpen(newOpen);
                    otherHalf.setBlockData(otherDoor, false);
                }

                sessions.put(uuid, newOpen);
                event.setCancelled(true);
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
                placeBlockAt(player, block.getLocation(), type, door);
            }
        }
        // Iron Trapdoor logic
        else if (type == Material.IRON_TRAPDOOR && isEmptyHand && player.isSneaking()) {
            if (block.getBlockData() instanceof org.bukkit.block.data.type.TrapDoor trapDoor) {
                boolean newOpen = !trapDoor.isOpen();
                trapDoor.setOpen(newOpen);
                block.setBlockData(trapDoor, false);

                sessions.put(uuid, newOpen);
                event.setCancelled(true);
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
                placeBlockAt(player, block.getLocation(), type, trapDoor);
            }
        }
        // Shift-place: use saved open state
        else if ((type == Material.IRON_DOOR || type == Material.IRON_TRAPDOOR) && player.isSneaking()) {
            var placeLoc = getPlaceableLocation(event);
            if (placeLoc != null) {
                event.setCancelled(true);
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
                if (type == Material.IRON_DOOR) {
                    Door door = (Door) type.createBlockData();
                    door.setOpen(sessions.getOrDefault(uuid, false));
                    placeBlockAt(player, placeLoc, type, door);
                } else if (type == Material.IRON_TRAPDOOR) {
                    org.bukkit.block.data.type.TrapDoor trapDoor = (org.bukkit.block.data.type.TrapDoor) type.createBlockData();
                    trapDoor.setOpen(sessions.getOrDefault(uuid, false));
                    placeBlockAt(player, placeLoc, type, trapDoor);
                }
            }
        }
    }

    private static boolean isIronDoorOrTrapdoor(Material mat) {
        return mat == Material.IRON_DOOR || mat == Material.IRON_TRAPDOOR;
    }
}