package com.sharedfate.client.mixin;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DeathScreen.class)
public interface DeathScreenAccessor {
	@Accessor("hardcore")
	@Mutable
	void sharedfate$setHardcore(boolean hardcore);

	/**
	 * 제목 아래 사인 줄.
	 *
	 * <p>화면이 그릴 때마다 이 항목을 읽으므로 창이 떠 있는 도중에 바꿔도 곧바로 반영된다.
	 * 서버가 팀원의 사망 메시지를 막아 이 자리가 비어 있을 때, 사망 알림을 켠 팀에서
	 * 「누가 죽었는가」 한 줄을 대신 채운다.
	 */
	@Accessor("causeOfDeath")
	@Mutable
	void sharedfate$setCauseOfDeath(Component causeOfDeath);
}
