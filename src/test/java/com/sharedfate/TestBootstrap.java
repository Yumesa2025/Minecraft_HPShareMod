package com.sharedfate;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

public final class TestBootstrap {
	private static boolean done = false;
	private static HolderLookup.Provider registries;

	private TestBootstrap() {
	}

	public static synchronized void ensureInitialized() {
		if (done) {
			return;
		}
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		registries = VanillaRegistries.createLookup();
		for (DataComponentInitializers.PendingComponents<?> pending
				: BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)) {
			pending.apply();
		}

		done = true;
	}

	/**
	 * 데이터팩 레지스트리까지 들어 있는 조회기.
	 *
	 * <p>인챈트처럼 {@code BuiltInRegistries} 에 없는 것을 시험에서 찾을 때 쓴다. 살아 있는
	 * 서버의 {@code registryAccess()} 자리에 그대로 넣을 수 있다.
	 */
	public static synchronized HolderLookup.Provider registries() {
		ensureInitialized();
		return registries;
	}
}
