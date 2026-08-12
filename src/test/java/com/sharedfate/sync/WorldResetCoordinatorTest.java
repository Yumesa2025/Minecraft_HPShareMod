package com.sharedfate.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldResetCoordinatorTest {

	@Test
	void 서버_바로_아래_월드만_초기화_대상으로_허용한다(@TempDir Path server) {
		Path world = server.resolve("world");

		assertEquals(world.toAbsolutePath().normalize(),
				WorldResetCoordinator.validateWorldDirectory(server, world));
		assertThrows(IllegalArgumentException.class,
				() -> WorldResetCoordinator.validateWorldDirectory(server, server));
		assertThrows(IllegalArgumentException.class,
				() -> WorldResetCoordinator.validateWorldDirectory(server, server.resolve("nested/world")));
		assertThrows(IllegalArgumentException.class,
				() -> WorldResetCoordinator.validateWorldDirectory(server, server.resolve("../outside")));
	}

	@Test
	void 표식에는_계약_버전과_정규화된_월드_경로가_들어간다(@TempDir Path server) {
		Path world = server.resolve("world");

		String marker = WorldResetCoordinator.markerContents(world);

		assertTrue(marker.startsWith(WorldResetCoordinator.MARKER_HEADER + System.lineSeparator()));
		assertTrue(marker.contains(world.toAbsolutePath().normalize().toString()));
	}
}
