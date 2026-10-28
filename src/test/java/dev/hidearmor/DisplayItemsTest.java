package dev.hidearmor;

import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemEquippable;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class DisplayItemsTest {
    @BeforeAll
    static void initialize() { PacketTestRuntime.initialize(); }

    @Test
    void independentlyHidesAllFivePartsIncludingCopperAndTurtle() {
        var cases = List.of(
                ItemTypes.DIAMOND_HELMET, ItemTypes.TURTLE_HELMET, ItemTypes.COPPER_HELMET,
                ItemTypes.DIAMOND_CHESTPLATE, ItemTypes.DIAMOND_LEGGINGS, ItemTypes.DIAMOND_BOOTS, ItemTypes.ELYTRA);
        var display = new DisplayItems();
        for (var type : cases) {
            var original = stack(type);
            var part = HidePart.of(original);
            assertNotNull(part);
            for (var selected : HidePart.values()) {
                var projected = display.project(original, selected.bit());
                assertEquals(selected == part, DisplayItems.isProjected(projected), type + " / " + selected);
                assertFalse(DisplayItems.isProjected(original));
            }
        }
    }

    @Test
    void allAndNoneHaveExpectedMasks() {
        for (var part : HidePart.values()) {
            assertTrue(part.hidden(HidePart.ALL));
            assertFalse(part.hidden(0));
            assertFalse(part.hidden(HidePart.ALL ^ part.bit()));
        }
        assertEquals(31, HidePart.ALL);
    }

    @Test
    void preservesIconCountDamageNameGliderAndAllOtherComponents() {
        var display = new DisplayItems();
        for (var type : List.of(ItemTypes.LEATHER_CHESTPLATE, ItemTypes.DIAMOND_HELMET, ItemTypes.ELYTRA)) {
            var original = stack(type);
            original.setComponent(ComponentTypes.DAMAGE, 23);
            original.setComponent(ComponentTypes.CUSTOM_NAME, Component.text("我的装备"));
            var originalEquipment = original.getComponent(ComponentTypes.EQUIPPABLE).orElseThrow();
            var originalPatches = original.getComponents().copy();
            var projected = display.project(original, HidePart.ALL);
            assertNotSame(original, projected);
            assertSame(original.getType(), projected.getType());
            assertEquals(original.getAmount(), projected.getAmount());
            assertEquals(23, projected.getComponent(ComponentTypes.DAMAGE).orElseThrow());
            assertEquals(original.getComponent(ComponentTypes.CUSTOM_NAME), projected.getComponent(ComponentTypes.CUSTOM_NAME));
            assertEquals(original.getComponent(ComponentTypes.GLIDER), projected.getComponent(ComponentTypes.GLIDER));
            assertNotSame(originalEquipment, projected.getComponent(ComponentTypes.EQUIPPABLE).orElseThrow());
            assertEquals(originalPatches, original.getComponents());
            var restored = display.restoreCreative(projected).orElseThrow();
            assertTrue(ItemStack.isSameItemSameComponents(original, restored));
            assertEquals(originalPatches.getPatches(), restored.getComponents().getPatches());
        }
    }

    @Test
    void defaultEquipmentComponentsAreNeverMutatedAcrossPlayers() {
        var first = stack(ItemTypes.DIAMOND_HELMET);
        var second = stack(ItemTypes.DIAMOND_HELMET);
        var before = second.getComponent(ComponentTypes.EQUIPPABLE).orElseThrow().getAssetId();
        var shown = new DisplayItems().project(first, HidePart.ALL);
        assertNotEquals(before, shown.getComponent(ComponentTypes.EQUIPPABLE).orElseThrow().getAssetId());
        assertEquals(before, first.getComponent(ComponentTypes.EQUIPPABLE).orElseThrow().getAssetId());
        assertEquals(before, second.getComponent(ComponentTypes.EQUIPPABLE).orElseThrow().getAssetId());
    }

    @Test
    void skullsPumpkinsWeaponsAndEmptyStacksRemainUntouched() {
        var display = new DisplayItems();
        for (var type : List.of(ItemTypes.PLAYER_HEAD, ItemTypes.CARVED_PUMPKIN, ItemTypes.DIAMOND_SWORD, ItemTypes.AIR)) {
            var original = stack(type);
            assertSame(original, display.project(original, HidePart.ALL));
        }
        assertSame(ItemStack.EMPTY, display.project(ItemStack.EMPTY, HidePart.ALL));
        assertEquals(0, display.size());
    }

    @Test
    void helmetRetainsNonemptyAssetAndEveryEquippingBehavior() {
        var original = stack(ItemTypes.NETHERITE_HELMET);
        var originalEquipment = original.getComponent(ComponentTypes.EQUIPPABLE).orElseThrow();
        var shown = new DisplayItems().project(original, HidePart.ALL).getComponent(ComponentTypes.EQUIPPABLE).orElseThrow();
        assertNotNull(shown.getAssetId());
        assertEquals(originalEquipment.getSlot(), shown.getSlot());
        assertEquals(originalEquipment.getEquipSound(), shown.getEquipSound());
        assertEquals(originalEquipment.getCameraOverlay(), shown.getCameraOverlay());
        assertEquals(originalEquipment.getAllowedEntities(), shown.getAllowedEntities());
        assertEquals(originalEquipment.isSwappable(), shown.isSwappable());
        assertEquals(originalEquipment.isDispensable(), shown.isDispensable());
        assertEquals(originalEquipment.isDamageOnHurt(), shown.isDamageOnHurt());
        assertEquals(originalEquipment.isEquipOnInteract(), shown.isEquipOnInteract());
        assertEquals(originalEquipment.isCanBeSheared(), shown.isCanBeSheared());
        assertEquals(originalEquipment.getShearingSound(), shown.getShearingSound());
    }

    @Test
    void restoresCustomEquipmentAssetExactlyAndPreservesCreativeCountChange() {
        var display = new DisplayItems();
        var original = withCustomAsset("original:unique");
        var projected = display.project(original, HidePart.ALL);
        projected.setAmount(2);
        var restored = display.restoreCreative(projected).orElseThrow();
        assertEquals(2, restored.getAmount());
        assertTrue(ItemStack.isSameItemSameComponents(original, restored));
        assertTrue(DisplayItems.isProjected(projected));
    }

    @Test
    void rejectsUnknownForeignExpiredOrTamperedProjections() {
        var display = new DisplayItems();
        var shown = display.project(stack(ItemTypes.DIAMOND_HELMET), HidePart.ALL);
        assertTrue(new DisplayItems().restoreCreative(shown).isEmpty());
        var otherType = stack(ItemTypes.IRON_HELMET);
        otherType.setComponent(ComponentTypes.EQUIPPABLE, shown.getComponent(ComponentTypes.EQUIPPABLE).orElseThrow());
        assertTrue(display.restoreCreative(otherType).isEmpty());
        var equipment = shown.getComponent(ComponentTypes.EQUIPPABLE).orElseThrow();
        equipment.setDamageOnHurt(!equipment.isDamageOnHurt());
        assertTrue(display.restoreCreative(shown).isEmpty());
        display.clear();
        assertEquals(0, display.size());
        assertFalse(display.hasProjectedItems());
    }

    @Test
    void boundsVariantsAndEvictedTokensFailClosed() {
        var display = new DisplayItems();
        var first = display.project(withCustomAsset("original:first"), HidePart.ALL);
        for (int i = 0; i < DisplayItems.MAX_VARIANTS + 10; i++) {
            display.project(withCustomAsset("original:variant_" + i), HidePart.ALL);
        }
        assertEquals(DisplayItems.MAX_VARIANTS, display.size());
        assertTrue(display.restoreCreative(first).isEmpty());
    }

    @Test
    void repeatedAndConcurrentProjectionDoesNotGrowCacheOrMutateOriginals() throws Exception {
        var display = new DisplayItems();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 8; worker++) {
                jobs.add(executor.submit(() -> {
                    for (int i = 0; i < 100; i++) {
                        var original = stack(ItemTypes.DIAMOND_HELMET);
                        var shown = display.project(original, HidePart.ALL);
                        assertTrue(ItemStack.isSameItemSameComponents(original, display.restoreCreative(shown).orElseThrow()));
                        assertFalse(DisplayItems.isProjected(original));
                    }
                }));
            }
            for (var job : jobs) job.get();
        }
        assertEquals(1, display.size());
    }

    @Test
    void otherPlayersKeepHandsAndElytraIsSeparateFromChestplate() {
        var sword = new Equipment(EquipmentSlot.MAIN_HAND, stack(ItemTypes.DIAMOND_SWORD));
        var helmetInHand = new Equipment(EquipmentSlot.MAIN_HAND, stack(ItemTypes.DIAMOND_HELMET));
        assertSame(sword, ArmorPackets.hideEquipment(sword, HidePart.ALL));
        assertSame(helmetInHand, ArmorPackets.hideEquipment(helmetInHand, HidePart.ALL));
        var wings = new Equipment(EquipmentSlot.CHEST_PLATE, stack(ItemTypes.ELYTRA));
        assertSame(wings, ArmorPackets.hideEquipment(wings, HidePart.CHESTPLATE.bit()));
        assertTrue(ArmorPackets.hideEquipment(wings, HidePart.ELYTRA.bit()).getItem().isEmpty());
        assertFalse(wings.getItem().isEmpty());
    }

    private static ItemStack stack(ItemType type) {
        return ItemStack.builder().type(type).build();
    }

    private static ItemStack withCustomAsset(String asset) {
        var item = stack(ItemTypes.DIAMOND_HELMET);
        var original = item.getComponent(ComponentTypes.EQUIPPABLE).orElseThrow();
        item.setComponent(ComponentTypes.EQUIPPABLE, new ItemEquippable(original.getSlot(), original.getEquipSound(),
                new ResourceLocation(asset), original.getCameraOverlay(), original.getAllowedEntities(),
                original.isDispensable(), original.isSwappable(), original.isDamageOnHurt(), original.isEquipOnInteract(),
                original.isCanBeSheared(), original.getShearingSound()));
        return item;
    }
}
