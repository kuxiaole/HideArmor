package dev.hidearmor;

import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.HashedComponentPatchMap;
import com.github.retrooper.packetevents.protocol.item.HashedStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InventoryPredictionTest {
    @BeforeAll
    static void initialize() { PacketTestRuntime.initialize(); }

    @ParameterizedTest
    @EnumSource(value = WrapperPlayClientClickWindow.WindowClickType.class, names = "UNKNOWN", mode = EnumSource.Mode.EXCLUDE)
    void preservesEveryClickActionAndRemovesOnlyUntrustedPredictions(WrapperPlayClientClickWindow.WindowClickType action) {
        var hash = new HashedStack(ItemTypes.DIAMOND_HELMET, 1,
                new HashedComponentPatchMap(Map.of(ComponentTypes.EQUIPPABLE, 123456), Set.of()));
        var packet = new WrapperPlayClientClickWindow(3, 7, 12, 1, action,
                Map.of(5, Optional.of(hash)), Optional.of(hash));
        ArmorPackets.authoritativeClick(packet);
        assertEquals(3, packet.getWindowId());
        assertEquals(12, packet.getSlot());
        assertEquals(1, packet.getButton());
        assertEquals(action, packet.getWindowClickType());
        assertEquals(-1, packet.getStateId().orElseThrow());
        assertTrue(packet.getHashedSlots().isEmpty());
        assertTrue(packet.getCarriedHashedStack().isEmpty());
    }
}
