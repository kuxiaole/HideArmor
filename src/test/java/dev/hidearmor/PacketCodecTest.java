package dev.hidearmor;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PacketCodecTest {
    @BeforeAll
    static void initialize() { PacketTestRuntime.initialize(); }

    @Test
    void projectedArmorRoundTripsThroughEveryInventoryPacket() {
        var display = new DisplayItems();
        var original = ItemStack.builder().type(ItemTypes.DIAMOND_HELMET).build();
        var shown = display.project(original, HidePart.HELMET.bit());

        var contents = new WrapperPlayServerWindowItems(0, 13, List.of(shown), shown);
        roundTrip(contents);
        assertRestorable(display, original, contents.getItems().getFirst());
        assertRestorable(display, original, contents.getCarriedItem().orElseThrow());

        var slot = new WrapperPlayServerSetSlot(0, 14, 5, shown);
        roundTrip(slot);
        assertEquals(5, slot.getSlot());
        assertRestorable(display, original, slot.getItem());

        var inventory = new WrapperPlayServerSetPlayerInventory(39, shown);
        roundTrip(inventory);
        assertEquals(39, inventory.getSlot());
        assertRestorable(display, original, inventory.getStack());

        var cursor = new WrapperPlayServerSetCursorItem(shown);
        roundTrip(cursor);
        assertRestorable(display, original, cursor.getStack());
    }

    @Test
    void creativeUntrustedItemCodecPreservesRecoverableProjection() {
        var display = new DisplayItems();
        var original = ItemStack.builder().type(ItemTypes.ELYTRA).build();
        var creative = new WrapperPlayClientCreativeInventoryAction(6, display.project(original, HidePart.ALL));
        roundTrip(creative);
        var recovered = display.restoreCreative(creative.getItemStack()).orElseThrow();
        assertTrue(ItemStack.isSameItemSameComponents(original, recovered));
        creative.setItemStack(recovered);
        roundTrip(creative);
        assertFalse(DisplayItems.isProjected(creative.getItemStack()));
        assertTrue(ItemStack.isSameItemSameComponents(original, creative.getItemStack()));
    }

    @Test
    void equipmentPacketRetainsUntouchedHandsAndClearsOnlySelectedArmor() {
        var entries = List.of(
                new Equipment(EquipmentSlot.HELMET, ItemStack.builder().type(ItemTypes.DIAMOND_HELMET).build()),
                new Equipment(EquipmentSlot.MAIN_HAND, ItemStack.builder().type(ItemTypes.DIAMOND_SWORD).build()));
        var packet = new WrapperPlayServerEntityEquipment(42,
                entries.stream().map(e -> ArmorPackets.hideEquipment(e, HidePart.HELMET.bit())).toList());
        roundTrip(packet);
        assertEquals(42, packet.getEntityId());
        assertTrue(packet.getEquipment().getFirst().getItem().isEmpty());
        assertEquals(ItemTypes.DIAMOND_SWORD, packet.getEquipment().getLast().getItem().getType());
        assertFalse(entries.getFirst().getItem().isEmpty());
    }

    private static void roundTrip(PacketWrapper<?> packet) {
        var buffer = Unpooled.buffer();
        try {
            packet.setBuffer(buffer);
            packet.write();
            packet.read();
            assertEquals(0, buffer.readableBytes(), "the 26.3 codec must consume the complete packet");
        } finally {
            packet.setBuffer(null);
            buffer.release();
        }
    }

    private static void assertRestorable(DisplayItems display, ItemStack original, ItemStack shown) {
        assertTrue(DisplayItems.isProjected(shown));
        assertTrue(ItemStack.isSameItemSameComponents(original, display.restoreCreative(shown).orElseThrow()));
    }
}
