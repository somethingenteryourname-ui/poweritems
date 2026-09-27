package com.poweritem;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PowerItem extends JavaPlugin {

    private NamespacedKey idKey;
    private NamespacedKey damageKey;
    private NamespacedKey modeKey;
    private NamespacedKey triggerKey;
    private NamespacedKey useTimeKey;

    @Override
    public void onEnable() {
        // These keys are stored on the item itself, so only that exact item gets the powers.
        idKey = new NamespacedKey(this, "id");
        damageKey = new NamespacedKey(this, "bonus_damage");
        modeKey = new NamespacedKey(this, "mode");
        triggerKey = new NamespacedKey(this, "trigger");
        useTimeKey = new NamespacedKey(this, "use_time");

        PowerItemCommand command = new PowerItemCommand(this);
        PluginCommand pc = getCommand("poweritem");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new HitListener(this), this);
        getLogger().info("PowerItem enabled!");
    }

    public NamespacedKey idKey() { return idKey; }
    public NamespacedKey damageKey() { return damageKey; }
    public NamespacedKey modeKey() { return modeKey; }
    public NamespacedKey triggerKey() { return triggerKey; }
    public NamespacedKey useTimeKey() { return useTimeKey; }
}
