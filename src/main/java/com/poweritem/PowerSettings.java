package com.poweritem;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Everything saved on one power item. */
public record PowerSettings(String id, Mode mode, double bonus, Trigger trigger, double useSeconds) {

    public static final double DEFAULT_USE_SECONDS = 3.0;

    /** Returns null if the item isn't a power item. */
    public static PowerSettings read(PowerItem plugin, ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(plugin.idKey(), PersistentDataType.STRING);
        if (id == null) return null;
        return new PowerSettings(
                id,
                Mode.from(pdc.get(plugin.modeKey(), PersistentDataType.STRING)),
                pdc.getOrDefault(plugin.damageKey(), PersistentDataType.DOUBLE, 0.0),
                Trigger.from(pdc.get(plugin.triggerKey(), PersistentDataType.STRING)),
                pdc.getOrDefault(plugin.useTimeKey(), PersistentDataType.DOUBLE, DEFAULT_USE_SECONDS));
    }
}
