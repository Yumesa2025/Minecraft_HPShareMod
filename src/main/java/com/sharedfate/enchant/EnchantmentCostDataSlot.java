package com.sharedfate.enchant;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import org.jetbrains.annotations.Nullable;

/**
 * 인챈트 한 번에 드는 다이아몬드 개수를 <b>클라이언트로 내려보내는 칸</b>입니다.
 *
 * <p>{@code enchant_cost} 증강을 가진 팀은 개수가 달라지는데, 팀은 서버만 압니다. 그런데
 * 단추의 숫자와 툴팁은 클라이언트가 그리므로 그쪽도 같은 숫자를 알아야 합니다. 메뉴에 붙는
 * 데이터 칸은 바닐라가 이미 값이 달라질 때마다 보내 주고 창을 열 때 한 번 전부 보내 주므로,
 * 여기에 얹으면 새 꾸러미(패킷)를 만들 필요가 없습니다.
 *
 * <p><b>서버는 {@link #get}, 클라이언트는 {@link #set} 만 지납니다.</b>
 * {@code AbstractContainerMenu.broadcastChanges} 는 서버에서만 돌며 {@code get()} 을 읽어
 * 비교하고, {@code setData} 는 클라이언트가 꾸러미를 받았을 때만 불립니다. 그래도 혹시 서버에서
 * {@code set} 이 불리는 일이 생겨도 화면용 값이 서버 판정에 새어 들지 않도록, 주인이 서버 쪽
 * 플레이어면 받아 두지 않습니다.
 *
 * <p>이 칸은 <b>메뉴 슬롯이 아니라 데이터 칸</b>이라 화면에 아무것도 그리지 않습니다. 다만 데이터
 * 칸의 개수가 달라지므로 이 판의 클라이언트만 접속할 수 있어야 합니다 — 막는 수단은 악수 규약
 * ({@code SharedFateNetworking.PROTOCOL_VERSION})뿐입니다.
 */
public final class EnchantmentCostDataSlot extends DataSlot {
	private final @Nullable Player owner;

	public EnchantmentCostDataSlot(@Nullable Player owner) {
		this.owner = owner;
	}

	/** 서버가 읽습니다. 주인의 팀이 실제로 내야 하는 개수입니다. */
	@Override
	public int get() {
		return EnchantmentDiamondCost.forPlayer(owner);
	}

	/** 클라이언트가 꾸러미를 받았을 때 불립니다. */
	@Override
	public void set(int value) {
		if (owner instanceof ServerPlayer) {
			return;
		}
		EnchantmentDiamondCost.rememberShown(value);
	}
}
