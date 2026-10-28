package dev.hidearmor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

final class ArmorMenu implements InventoryHolder {
    static final int HIDE_ALL = 20;
    static final int SHOW_ALL = 22;
    static final int CLOSE = 24;
    final UUID owner;
    private final Inventory inventory;

    ArmorMenu(UUID owner, int selectedMask) {
        this.owner = owner;
        inventory = Bukkit.createInventory(this, 27, Component.text("盔甲隐藏控制", NamedTextColor.DARK_AQUA));
        render(selectedMask);
    }

    void render(int mask) {
        for (var part : HidePart.values()) {
            boolean hidden = part.hidden(mask);
            inventory.setItem(part.menuSlot, item(Material.valueOf(part.icon),
                    part.label + " · " + (hidden ? "已隐藏" : "已显示"),
                    hidden ? NamedTextColor.GRAY : NamedTextColor.GREEN,
                    "点击" + (hidden ? "显示" : "隐藏") + part.label,
                    part == HidePart.ELYTRA ? "独立控制外观，不影响飞行" : "只改变外观，装备属性保持正常"));
        }
        inventory.setItem(HIDE_ALL, item(Material.ENDER_EYE, "一键隐藏", NamedTextColor.AQUA,
                "隐藏四件盔甲和鞘翅", "自己和其他玩家都看不到"));
        inventory.setItem(SHOW_ALL, item(Material.ARMOR_STAND, "一键显示", NamedTextColor.GREEN,
                "显示四件盔甲和鞘翅", "选择会自动保存"));
        inventory.setItem(CLOSE, item(Material.BARRIER, "关闭菜单", NamedTextColor.RED,
                "输入 /ha 可以再次打开"));
    }

    private static ItemStack item(Material material, String title, NamedTextColor color, String... lines) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(title, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(lines).stream().map(line ->
                Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
