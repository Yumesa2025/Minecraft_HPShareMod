package com.sharedfate;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

public final class TestBootstrap {
	private static boolean done = false;

	private TestBootstrap() {
	}

	public static synchronized void ensureInitialized() {
		if (done) {
			return;
		}
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		HolderLookup.Provider registries = VanillaRegistries.createLookup();
		for (DataComponentInitializers.PendingComponents<?> pending
				: BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)) {
			pending.apply();
		}

		done = true;
	}
}
