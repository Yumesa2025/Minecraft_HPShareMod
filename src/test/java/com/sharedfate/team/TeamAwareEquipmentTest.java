package com.sharedfate.team;

import com.sharedfate.TestBootstrap;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamAwareEquipmentTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 팀_저장소가_생기면_재생성_없이_공유_장비로_전환한다() {
		AtomicReference<SharedEquipmentStore> current = new AtomicReference<>();
		TeamAwareEquipment equipment = new TeamAwareEquipment(null, current::get);
		ItemStack personal = new ItemStack(Items.IRON_CHESTPLATE);
		equipment.set(EquipmentSlot.CHEST, personal);

		SharedEquipmentStore shared = new SharedEquipmentStore();
		current.set(shared);
		ItemStack team = new ItemStack(Items.DIAMOND_CHESTPLATE);
		equipment.set(EquipmentSlot.CHEST, team);

		assertSame(team, shared.get(EquipmentSlot.CHEST));
		assertSame(team, equipment.get(EquipmentSlot.CHEST));
		current.set(null);
		assertSame(personal, equipment.get(EquipmentSlot.CHEST));
	}

	@Test
	void 팀_상태에서는_개인_NBT_setAll을_무시한다() {
		SharedEquipmentStore shared = new SharedEquipmentStore();
		ItemStack team = new ItemStack(Items.DIAMOND_HELMET);
		shared.set(EquipmentSlot.HEAD, team);
		TeamAwareEquipment equipment = new TeamAwareEquipment(null, () -> shared);
		EntityEquipment personalNbt = new EntityEquipment();
		personalNbt.set(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));

		equipment.setAll(personalNbt);

		assertSame(team, equipment.get(EquipmentSlot.HEAD));
	}

	@Test
	void clear는_현재_공유_저장소를_비운다() {
		SharedEquipmentStore shared = new SharedEquipmentStore();
		shared.set(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		TeamAwareEquipment equipment = new TeamAwareEquipment(null, () -> shared);
		assertFalse(equipment.isEmpty());

		equipment.clear();

		assertTrue(shared.isEmpty());
		assertTrue(equipment.isEmpty());
	}
}
