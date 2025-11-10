 package net.greenfieldmc.core.advancedbuild.handlers;

import net.greenfieldmc.core.advancedbuild.InteractPredicate;
import net.greenfieldmc.core.advancedbuild.InteractionHandler;
import net.greenfieldmc.core.shared.services.ICoreProtectService;
import net.greenfieldmc.core.shared.services.IWorldEditService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SwitchInteraction extends InteractionHandler {

    private final Map<UUID, Boolean> sessions = new HashMap<>();

    public SwitchInteraction(IWorldEditService worldEditService, ICoreProtectService coreProtectService) {
        super(worldEditService, coreProtectService, (InteractPredicate) (event) -> {
            var player = event.getPlayer();
            var mainHand = player.getInventory().getItemInMainHand().getType();
            var clicked = event.getClickedBlock();
            if (clicked == null) return false;
            return (mainHand == Material.AIR && isSwitch(clicked.getType()))
                    || (mainHand.isBlock() && isSwitch(mainHand));
        });
    }

    @Override
    public TextComponent getInteractionDescription() {
        return Component.text("Allow unnatural placement and state control of switches (buttons, levers, etc). Shift-right-click to toggle, shift-place to use saved state.");
    }

    @Override
    public TextComponent getInteractionUsage() {
        return Component.text("Shift and right click to place or toggle a switch type block. Shift-place to use saved powered state.");
    }

    @Override
    public TextComponent getMaterialListText() {
        return Component.text("Any block that is a button or lever.");
    }

    @Override
    public void onRightClickBlock(PlayerInteractEvent event) {
        var player = event.getPlayer();
        var handMat = getHandMat(event);
        Block block = event.getClickedBlock();
        UUID uuid = player.getUniqueId();

        // Shift right-click with empty hand: toggle powered state and save to session
        if (block != null && player.isSneaking() && player.getInventory().getItemInMainHand().getType() == Material.AIR && isSwitch(block.getType())) {
            if (block.getBlockData() instanceof Switch sw) {
                boolean newPowered = !sw.isPowered();
                sw.setPowered(newPowered);
                block.setBlockData(sw, false);
                sessions.put(uuid, newPowered);

                event.setCancelled(true);
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
                placeBlockAt(player, block.getLocation(), block.getType(), sw);
            }
        }
        // Shift-place: use saved powered state
        else if (player.isSneaking() && handMat.isBlock() && isSwitch(handMat)) {
            var placementLocation = getPlaceableLocation(event);
            if (placementLocation == null) return;

            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);

            Switch sw = (Switch) handMat.createBlockData();
            sw.setPowered(sessions.getOrDefault(uuid, false));

            // Placement logic for face/facing
            if (event.getBlockFace() == BlockFace.DOWN) sw.setAttachedFace(FaceAttachable.AttachedFace.CEILING);
            else if (event.getBlockFace() == BlockFace.UP) sw.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            else sw.setAttachedFace(FaceAttachable.AttachedFace.WALL);

            if (!sw.getFaces().contains(event.getBlockFace())) sw.setFacing(player.getFacing().getOppositeFace());
            else sw.setFacing(event.getBlockFace());

            placeBlockAt(player, placementLocation, handMat, sw);
        }
    }

    private static boolean isSwitch(Material mat) {
        return mat.name().endsWith("_BUTTON") || mat == Material.LEVER;
    }
}