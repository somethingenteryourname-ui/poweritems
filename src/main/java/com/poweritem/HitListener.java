package com.poweritem;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HitListener implements Listener {

    private final PowerItem plugin;
    // victim -> killer name, used to set a custom death message
    private final Map<UUID, String> pendingKills = new HashMap<>();
    // player -> server tick they last did a normal left-click attack
    private final Map<UUID, Integer> meleeTick = new HashMap<>();
    // player -> the power item they last used (right-clicked) and when that runs out
    private final Map<UUID, UseRecord> uses = new HashMap<>();

    private record UseRecord(PowerSettings settings, long expiresAt) { }

    public HitListener(PowerItem plugin) {
        this.plugin = plugin;
    }

    // ---------- Tracking normal attacks vs. using the item ----------

    // Fires right before a normal left-click attack, so we know that damage is a regular hit.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PrePlayerAttackEntityEvent event) {
        if (event.willAttack()) {
            meleeTick.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
        }
    }

    // Right-clicking air or a block with the item.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        recordUse(event.getPlayer(), event.getItem());
    }

    // Right-clicking a mob or player with the item.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onUseOnEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        recordUse(player, player.getInventory().getItem(event.getHand()));
    }

    private void recordUse(Player player, ItemStack item) {
        PowerSettings settings = PowerSettings.read(plugin, item);
        if (settings == null || settings.trigger() == Trigger.ATTACK) return;
        long expires = System.currentTimeMillis() + (long) (settings.useSeconds() * 1000);
        uses.put(player.getUniqueId(), new UseRecord(settings, expires));
    }

    // Returns the power item's settings if the player used one recently (or is still holding it charged up).
    private PowerSettings activeUse(Player player) {
        UseRecord record = uses.get(player.getUniqueId());
        if (record == null) return null;
        if (System.currentTimeMillis() <= record.expiresAt()) return record.settings();

        // Still holding right-click with the same item (charging a spear/bow/trident etc.)
        PowerSettings holding = PowerSettings.read(plugin, player.getActiveItem());
        if (holding != null && holding.id().equals(record.settings().id())) return record.settings();

        uses.remove(player.getUniqueId());
        return null;
    }

    // ---------- Damage ----------

    // ignoreCancelled = true means if PvP is off or a region protects them, we do nothing.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null) return;
        if (!(event.getEntity() instanceof LivingEntity victim) || victim.equals(attacker)) return;

        boolean normalAttackThisTick =
                Integer.valueOf(Bukkit.getCurrentTick()).equals(meleeTick.get(attacker.getUniqueId()));
        boolean directHit = event.getDamager() == attacker && event.getCause() == DamageCause.ENTITY_ATTACK;

        PowerSettings settings = null;

        // Damage that came from using the item (not a normal left-click hit)
        PowerSettings used = normalAttackThisTick ? null : activeUse(attacker);
        if (used != null) {
            settings = used;
        } else if (directHit) {
            // Normal melee hit: use the held item, unless it's set to "use" only
            PowerSettings held = PowerSettings.read(plugin, attacker.getInventory().getItemInMainHand());
            if (held != null && held.trigger() != Trigger.USE) {
                settings = held;
            }
        }

        if (settings == null) return;
        applyPower(event, victim, attacker, settings);
    }

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private void applyPower(EntityDamageByEntityEvent event, LivingEntity victim, Player attacker, PowerSettings s) {
        if (s.mode() == Mode.KILL || s.mode() == Mode.POP) {
            // Cancelling the hit means no normal damage happens, so armor takes no durability damage.
            event.setCancelled(true);

            if (s.mode() == Mode.POP && popTotem(victim, event.getDamage())) {
                return; // they had a totem and it popped
            }
            kill(victim, attacker);
            return;
        }

        if (s.bonus() > 0) {
            event.setDamage(event.getDamage() + s.bonus());
        }
    }

    private void kill(LivingEntity victim, Player attacker) {
        victim.setKiller(attacker); // so the kill counts for the attacker
        if (victim instanceof Player) {
            pendingKills.put(victim.getUniqueId(), attacker.getName());
        }
        // Setting health to 0 skips totems completely.
        victim.setHealth(0);
        pendingKills.remove(victim.getUniqueId());
    }

    // Returns true if the victim was holding a totem and it got popped.
    private boolean popTotem(LivingEntity victim, double hitDamage) {
        EntityEquipment equipment = victim.getEquipment();
        if (equipment == null) return false;

        EquipmentSlot slot = null;
        if (equipment.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING) {
            slot = EquipmentSlot.HAND;
        } else if (equipment.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
            slot = EquipmentSlot.OFF_HAND;
        }
        if (slot == null) return false;

        // Use up the totem
        ItemStack totem = equipment.getItem(slot);
        totem.setAmount(totem.getAmount() - 1);
        equipment.setItem(slot, totem);

        // Same thing a real totem does
        victim.setHealth(1.0);
        for (PotionEffect effect : victim.getActivePotionEffects()) {
            victim.removePotionEffect(effect.getType());
        }
        victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));
        victim.playEffect(EntityEffect.TOTEM_RESURRECT); // totem animation + sound

        if (victim instanceof Player player) {
            player.incrementStatistic(Statistic.USE_ITEM, Material.TOTEM_OF_UNDYING);
        }

        // Normal hit cooldown so totems can't be popped super fast
        victim.setNoDamageTicks(victim.getMaximumNoDamageTicks());
        victim.setLastDamage(hitDamage);
        return true;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        String killer = pendingKills.get(event.getPlayer().getUniqueId());
        if (killer != null) {
            event.deathMessage(Component.text(
                    event.getPlayer().getName() + " was obliterated by " + killer, NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        meleeTick.remove(id);
        uses.remove(id);
    }
}
