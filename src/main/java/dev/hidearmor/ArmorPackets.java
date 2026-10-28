package dev.hidearmor;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.HashedStack;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import java.util.Map;
import java.util.Optional;

/** No Bukkit objects are read or written from these packet callbacks. */
final class ArmorPackets extends PacketListenerAbstract {
    private final HideArmorPlugin plugin;

    ArmorPackets(HideArmorPlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled() || event.getUser().getUUID() == null) return;
        var state = plugin.sessions.get(event.getUser().getUUID());
        if (state == null || state.closed.get() || state.user != event.getUser()) return;
        var type = event.getPacketType();
        if (type == PacketType.Play.Server.SPAWN_ENTITY) {
            var packet = new WrapperPlayServerSpawnEntity(event);
            if (packet.getEntityType() == EntityTypes.PLAYER) {
                packet.getUUID().ifPresent(uuid -> state.trackedPlayers.put(packet.getEntityId(), uuid));
            }
        } else if (type == PacketType.Play.Server.DESTROY_ENTITIES) {
            for (int id : new WrapperPlayServerDestroyEntities(event).getEntityIds()) state.trackedPlayers.remove(id);
        } else if (type == PacketType.Play.Server.RESPAWN) {
            state.trackedPlayers.clear();
            event.getTasksAfterSend().add(() -> plugin.requestRefresh(state));
        } else if (type == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            var packet = new WrapperPlayServerEntityEquipment(event);
            var targetId = state.trackedPlayers.get(packet.getEntityId());
            var target = targetId == null ? null : plugin.sessions.get(targetId);
            if (target != null && target.effectiveMask != 0) {
                int mask = target.effectiveMask;
                packet.setEquipment(packet.getEquipment().stream().map(entry -> hideEquipment(entry, mask)).toList());
                event.markForReEncode(true);
            }
        } else if (type == PacketType.Play.Server.WINDOW_ITEMS && state.effectiveMask != 0) {
            var packet = new WrapperPlayServerWindowItems(event);
            int mask = state.effectiveMask;
            packet.setItems(packet.getItems().stream().map(item -> state.display.project(item, mask)).toList());
            packet.getCarriedItem().ifPresent(item -> packet.setCarriedItem(state.display.project(item, mask)));
            event.markForReEncode(true);
        } else if (type == PacketType.Play.Server.SET_SLOT && state.effectiveMask != 0) {
            var packet = new WrapperPlayServerSetSlot(event);
            packet.setItem(state.display.project(packet.getItem(), state.effectiveMask));
            event.markForReEncode(true);
        } else if (type == PacketType.Play.Server.SET_PLAYER_INVENTORY && state.effectiveMask != 0) {
            var packet = new WrapperPlayServerSetPlayerInventory(event);
            packet.setStack(state.display.project(packet.getStack(), state.effectiveMask));
            event.markForReEncode(true);
        } else if (type == PacketType.Play.Server.SET_CURSOR_ITEM && state.effectiveMask != 0) {
            var packet = new WrapperPlayServerSetCursorItem(event);
            packet.setStack(state.display.project(packet.getStack(), state.effectiveMask));
            event.markForReEncode(true);
        }
    }

    static Equipment hideEquipment(Equipment equipment, int mask) {
        var part = HidePart.of(equipment.getItem());
        if (part != null && part.hidden(mask) && part.equipmentSlot == equipment.getSlot()) {
            return new Equipment(equipment.getSlot(), ItemStack.EMPTY);
        }
        return equipment;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled() || event.getUser().getUUID() == null) return;
        var state = plugin.sessions.get(event.getUser().getUUID());
        if (state == null || state.closed.get() || state.user != event.getUser()) return;
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW && state.display.hasProjectedItems()) {
            authoritativeClick(new WrapperPlayClientClickWindow(event));
            event.markForReEncode(true);
        } else if (event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            var packet = new WrapperPlayClientCreativeInventoryAction(event);
            if (!DisplayItems.isProjected(packet.getItemStack())) return;
            var restored = state.display.restoreCreative(packet.getItemStack());
            if (restored.isPresent()) {
                packet.setItemStack(restored.get());
                event.markForReEncode(true);
            } else {
                event.setCancelled(true);
            }
            plugin.requestRefresh(state);
        }
    }

    static void authoritativeClick(WrapperPlayClientClickWindow packet) {
        // 26.3 still executes the click when the state ID differs, then sends its complete authoritative state.
        // PacketEvents cannot calculate Mojang's component hashes. Do not call HashedStack.fromItemStack().
        packet.setStateID(Optional.of(-1));
        packet.setHashedSlots(Map.of());
        packet.setCarriedHashedStack((HashedStack) null);
    }
}
