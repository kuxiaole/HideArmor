package dev.hidearmor;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.ItemTags;

enum HidePart {
    HELMET("头盔", "DIAMOND_HELMET", 10, EquipmentSlot.HELMET),
    CHESTPLATE("胸甲", "DIAMOND_CHESTPLATE", 11, EquipmentSlot.CHEST_PLATE),
    LEGGINGS("护腿", "DIAMOND_LEGGINGS", 12, EquipmentSlot.LEGGINGS),
    BOOTS("靴子", "DIAMOND_BOOTS", 13, EquipmentSlot.BOOTS),
    ELYTRA("鞘翅", "ELYTRA", 14, EquipmentSlot.CHEST_PLATE);

    static final int ALL = (1 << values().length) - 1;
    final String label;
    final String icon;
    final int menuSlot;
    final EquipmentSlot equipmentSlot;

    HidePart(String label, String icon, int menuSlot, EquipmentSlot equipmentSlot) {
        this.label = label;
        this.icon = icon;
        this.menuSlot = menuSlot;
        this.equipmentSlot = equipmentSlot;
    }

    int bit() {
        return 1 << ordinal();
    }

    boolean hidden(int mask) {
        return (mask & bit()) != 0;
    }

    static HidePart of(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        var type = item.getType();
        if (type == ItemTypes.ELYTRA) return ELYTRA;
        if (ItemTags.HEAD_ARMOR.contains(type)) return HELMET;
        if (ItemTags.CHEST_ARMOR.contains(type)) return CHESTPLATE;
        if (ItemTags.LEG_ARMOR.contains(type)) return LEGGINGS;
        if (ItemTags.FOOT_ARMOR.contains(type)) return BOOTS;
        return null;
    }
}
