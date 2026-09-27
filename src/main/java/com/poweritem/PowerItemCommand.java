package com.poweritem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PowerItemCommand implements TabExecutor {

    private final PowerItem plugin;

    public PowerItemCommand(PowerItem plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            error(player, "Hold the item you want to change in your main hand.");
            return true;
        }

        if (args.length == 0) {
            usage(player, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "damage" -> {
                if (args.length < 2) {
                    error(player, "Usage: /" + label + " damage <amount>   (2 = one heart)");
                    return true;
                }
                Double amount = parseNumber(player, args[1]);
                if (amount == null) return true;
                item.editMeta(meta -> {
                    stampId(meta);
                    meta.getPersistentDataContainer().set(plugin.damageKey(), PersistentDataType.DOUBLE, amount);
                });
                player.getInventory().setItemInMainHand(item);
                ok(player, "This item now does +" + amount + " extra damage (" + (amount / 2) + " hearts).");
            }
            case "mode" -> {
                if (args.length < 2) {
                    error(player, "Usage: /" + label + " mode <normal|kill|pop>");
                    return true;
                }
                Mode mode;
                try {
                    mode = Mode.valueOf(args[1].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    error(player, "Mode must be normal, kill, or pop.");
                    return true;
                }
                item.editMeta(meta -> {
                    stampId(meta);
                    meta.getPersistentDataContainer().set(plugin.modeKey(), PersistentDataType.STRING, mode.name());
                });
                player.getInventory().setItemInMainHand(item);
                String explain = switch (mode) {
                    case NORMAL -> "normal hits.";
                    case KILL -> "every hit kills, totems and armor don't matter.";
                    case POP -> "every hit pops their totem (or kills them if they don't have one).";
                };
                ok(player, "Mode set to " + mode.name().toLowerCase(Locale.ROOT) + ": " + explain);
            }
            case "trigger" -> {
                if (args.length < 2) {
                    error(player, "Usage: /" + label + " trigger <attack|use|both>");
                    return true;
                }
                Trigger trigger;
                try {
                    trigger = Trigger.valueOf(args[1].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    error(player, "Trigger must be attack, use, or both.");
                    return true;
                }
                item.editMeta(meta -> {
                    stampId(meta);
                    meta.getPersistentDataContainer().set(plugin.triggerKey(), PersistentDataType.STRING, trigger.name());
                });
                player.getInventory().setItemInMainHand(item);
                String explain = switch (trigger) {
                    case ATTACK -> "powers only work when you hit someone normally.";
                    case USE -> "powers only work on damage from using the item (right-click), not normal hits.";
                    case BOTH -> "powers work on normal hits and when you use the item.";
                };
                ok(player, "Trigger set to " + trigger.name().toLowerCase(Locale.ROOT) + ": " + explain);
            }
            case "usetime" -> {
                if (args.length < 2) {
                    error(player, "Usage: /" + label + " usetime <seconds>");
                    return true;
                }
                Double seconds = parseNumber(player, args[1]);
                if (seconds == null) return true;
                item.editMeta(meta -> {
                    stampId(meta);
                    meta.getPersistentDataContainer().set(plugin.useTimeKey(), PersistentDataType.DOUBLE, seconds);
                });
                player.getInventory().setItemInMainHand(item);
                ok(player, "After using this item, its powers stay active for " + seconds + " seconds.");
            }
            case "clear" -> {
                item.editMeta(meta -> {
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.remove(plugin.idKey());
                    pdc.remove(plugin.damageKey());
                    pdc.remove(plugin.modeKey());
                    pdc.remove(plugin.triggerKey());
                    pdc.remove(plugin.useTimeKey());
                });
                player.getInventory().setItemInMainHand(item);
                ok(player, "Removed all powers from this item.");
            }
            case "info" -> {
                PowerSettings s = PowerSettings.read(plugin, item);
                if (s == null) {
                    error(player, "This item has no powers.");
                    return true;
                }
                String info = "Mode: " + s.mode().name().toLowerCase(Locale.ROOT)
                        + " | Bonus damage: +" + s.bonus()
                        + " | Trigger: " + s.trigger().name().toLowerCase(Locale.ROOT);
                if (s.trigger() != Trigger.ATTACK) {
                    info += " | Use time: " + s.useSeconds() + "s";
                }
                ok(player, info);
            }
            default -> usage(player, label);
        }
        return true;
    }

    private Double parseNumber(Player player, String text) {
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            error(player, "'" + text + "' isn't a number.");
            return null;
        }
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            error(player, "The number has to be 0 or more.");
            return null;
        }
        return value;
    }

    // Gives the item a unique ID so it's its own item and won't stack with normal copies.
    private void stampId(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(plugin.idKey(), PersistentDataType.STRING)) {
            pdc.set(plugin.idKey(), PersistentDataType.STRING, UUID.randomUUID().toString());
        }
    }

    private void usage(Player player, String label) {
        player.sendMessage(Component.text("PowerItem commands:", NamedTextColor.GOLD));
        line(player, "/" + label + " damage <amount>", "extra damage (2 = one heart)");
        line(player, "/" + label + " mode <normal|kill|pop>", "kill or totem-pop on hit");
        line(player, "/" + label + " trigger <attack|use|both>", "normal hits, using the item, or both");
        line(player, "/" + label + " usetime <seconds>", "how long powers last after using it");
        line(player, "/" + label + " info", "see this item's powers");
        line(player, "/" + label + " clear", "remove all powers");
    }

    private void line(Player player, String cmd, String desc) {
        player.sendMessage(Component.text(cmd, NamedTextColor.YELLOW)
                .append(Component.text(" - " + desc, NamedTextColor.GRAY)));
    }

    private void ok(Player player, String msg) {
        player.sendMessage(Component.text(msg, NamedTextColor.GREEN));
    }

    private void error(Player player, String msg) {
        player.sendMessage(Component.text(msg, NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("damage", "mode", "trigger", "usetime", "info", "clear"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "mode" -> options.addAll(List.of("normal", "kill", "pop"));
                case "trigger" -> options.addAll(List.of("attack", "use", "both"));
                case "damage" -> options.addAll(List.of("2", "5", "10", "20"));
                case "usetime" -> options.addAll(List.of("1", "3", "5", "10"));
                default -> { }
            }
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.startsWith(typed));
        return options;
    }
}
