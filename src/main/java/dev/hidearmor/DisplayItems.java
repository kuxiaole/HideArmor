package dev.hidearmor;

import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemEquippable;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A bounded, per-connection map. Its lock never encloses I/O or Bukkit calls. */
final class DisplayItems {
    static final int MAX_VARIANTS = 256;
    private final String prefix = "hidden/" + UUID.randomUUID().toString().replace("-", "") + "/";
    private final Map<ResourceLocation, Original> originals = new LinkedHashMap<>();
    private long sequence;
    private volatile boolean projected;

    synchronized ItemStack project(ItemStack item, int mask) {
        var part = HidePart.of(item);
        if (part == null || !part.hidden(mask)) return item;
        var equipment = item.getComponent(ComponentTypes.EQUIPPABLE).orElse(null);
        if (equipment == null || equipment.getSlot() != part.equipmentSlot || isProjected(item)) return item;

        ResourceLocation id = null;
        for (var entry : originals.entrySet()) {
            var original = entry.getValue();
            if (original.type == item.getType() && original.equipment.equals(equipment)) {
                id = entry.getKey();
                break;
            }
        }
        if (id == null) {
            if (originals.size() == MAX_VARIANTS) {
                originals.remove(originals.keySet().iterator().next());
            }
            id = new ResourceLocation("hidearmor", prefix + sequence++);
            originals.put(id, new Original(item.getType(), copy(equipment, equipment.getAssetId())));
        }

        var result = item.copy();
        // ItemStack.copy() does not clone nested component values. Never mutate equipment itself.
        result.setComponent(ComponentTypes.EQUIPPABLE, copy(equipment, id));
        projected = true;
        return result;
    }

    synchronized Optional<ItemStack> restoreCreative(ItemStack item) {
        if (!isProjected(item)) return Optional.of(item);
        var shown = item.getComponent(ComponentTypes.EQUIPPABLE).orElseThrow();
        var original = originals.get(shown.getAssetId());
        if (original == null || original.type != item.getType()) return Optional.empty();
        // Only recover a component issued by this connection. Never accept client-supplied originals.
        if (!copy(original.equipment, shown.getAssetId()).equals(shown)) return Optional.empty();
        var result = item.copy();
        result.setComponent(ComponentTypes.EQUIPPABLE,
                copy(original.equipment, original.equipment.getAssetId()));
        return Optional.of(result);
    }

    static boolean isProjected(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        var equipment = item.getComponent(ComponentTypes.EQUIPPABLE).orElse(null);
        var id = equipment == null ? null : equipment.getAssetId();
        return id != null && id.toString().startsWith("hidearmor:hidden/");
    }

    boolean hasProjectedItems() {
        // Retain until disconnect: the cursor or a saved creative hotbar may contain an older projection.
        return projected;
    }

    synchronized int size() {
        return originals.size();
    }

    synchronized void clear() {
        originals.clear();
        projected = false;
    }

    private static ItemEquippable copy(ItemEquippable source, ResourceLocation asset) {
        return new ItemEquippable(source.getSlot(), source.getEquipSound(), asset,
                source.getCameraOverlay(), source.getAllowedEntities(), source.isDispensable(),
                source.isSwappable(), source.isDamageOnHurt(), source.isEquipOnInteract(),
                source.isCanBeSheared(), source.getShearingSound());
    }

    private record Original(ItemType type, ItemEquippable equipment) {}
}
