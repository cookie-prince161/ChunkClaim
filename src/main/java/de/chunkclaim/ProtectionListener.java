package de.chunkclaim;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static de.chunkclaim.Messages.text;

/** Schützt geclaimte Chunks und zeigt die Meldungen. */
public final class ProtectionListener implements Listener {

    private static final long MESSAGE_COOLDOWN_MS = 400;

    private final ChunkClaimPlugin plugin;
    private final ClaimManager claims;
    private final Messages messages;
    private final Map<UUID, Long> lastDeny = new HashMap<>();

    public ProtectionListener(ChunkClaimPlugin plugin) {
        this.plugin = plugin;
        this.claims = plugin.claims();
        this.messages = plugin.messages();
    }

    // --- Hilfsmethoden -----------------------------------------------------

    /** true = Aktion ist verboten. Zeigt (wenn notify) die rote Meldung. */
    private boolean denied(Player player, Claim claim, Flag flag, boolean notify) {
        if (claim == null || claims.canAccess(player, claim, flag)) return false;
        if (notify) notifyDenied(player, flag);
        return true;
    }

    private void notifyDenied(Player player, Flag flag) {
        long now = System.currentTimeMillis();
        Long last = lastDeny.get(player.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) return;
        lastDeny.put(player.getUniqueId(), now);
        messages.display(player, "deny", messages.get("deny." + flag.id()));
        messages.sound(player, "deny");
    }

    // --- Abbauen / Bauen ---------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (denied(event.getPlayer(), claims.get(event.getBlock()), Flag.BREAK, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (denied(event.getPlayer(), claims.get(event.getBlockPlaced()), Flag.PLACE, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        boolean blocked = denied(player, claims.get(event.getBlock()), Flag.PLACE, true)
                || denied(player, claims.get(event.getBlockClicked()), Flag.PLACE, true);
        if (blocked) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        boolean blocked = denied(player, claims.get(event.getBlock()), Flag.BREAK, true)
                || denied(player, claims.get(event.getBlockClicked()), Flag.BREAK, true);
        if (blocked) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (denied(player, claims.get(event.getBlock()), Flag.PLACE, true)) {
            event.setCancelled(true);
        }
    }

    // --- Benutzen (Truhen, Türen, Knöpfe, Hebel ...) -----------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Claim claim = claims.get(block);
        if (claim == null) return;

        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            // Druckplatten, Ackerland zertrampeln: ohne Meldung blockieren
            if (denied(event.getPlayer(), claim, Flag.INTERACT, false)) event.setCancelled(true);
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK) return;

        boolean notify = event.getHand() == EquipmentSlot.HAND;
        if (denied(event.getPlayer(), claim, Flag.INTERACT, notify)) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame) && !(entity instanceof ArmorStand)) return;
        if (denied(event.getPlayer(), claims.get(entity.getLocation()), Flag.INTERACT, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (denied(event.getPlayer(), claims.get(event.getRightClicked().getLocation()), Flag.INTERACT, true)) {
            event.setCancelled(true);
        }
    }

    // --- Bilderrahmen, Gemälde ---------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player player)) return;
        if (denied(player, claims.get(event.getEntity().getLocation()), Flag.BREAK, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (denied(player, claims.get(event.getEntity().getLocation()), Flag.PLACE, true)) {
            event.setCancelled(true);
        }
    }

    // --- Explosionen -------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.explosions", true)) return;
        event.blockList().removeIf(block -> claims.get(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.explosions", true)) return;
        event.blockList().removeIf(block -> claims.get(block) != null);
    }

    // --- Betreten-Meldung --------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4) && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) return;
        if (!plugin.getConfig().getBoolean("notify.on-enter", true)) return;

        Player player = event.getPlayer();
        Claim entered = claims.get(to);
        if (entered == null) return;

        Claim left = claims.get(from);
        if (left != null && left.owner().equals(entered.owner())) return; // gleicher Besitzer: keine neue Meldung
        if (entered.isOwner(player) || entered.isTrusted(player.getUniqueId())) return;

        messages.display(player, "enter", messages.get("enter.foreign", text("owner", entered.ownerName())));
        messages.sound(player, "enter");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastDeny.remove(event.getPlayer().getUniqueId());
    }
}
