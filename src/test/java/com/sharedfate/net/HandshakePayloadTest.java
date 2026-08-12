package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import com.sharedfate.config.SharedFateConfig;
import com.sharedfate.inventory.ExpandedInventoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandshakePayloadTest {
	private SharedFateConfig previousConfig;

	@BeforeEach
	void setUp() {
		previousConfig = SharedFateMod.config;
		SharedFateMod.config = new SharedFateConfig();
		SharedFateMod.config.mainInventoryRows = 3;
	}

	@AfterEach
	void tearDown() {
		ExpandedInventoryManager.clearNegotiatedClientLayout();
		SharedFateMod.config = previousConfig;
	}

	@Test
	void 일반_실행은_바닐라_메뉴_구성을_광고한다() {
		HandshakePayload payload = HandshakePayload.current();

		assertEquals(SharedFateNetworking.PROTOCOL_VERSION, payload.protocolVersion());
		assertEquals(HandshakePayload.THREE_ROW_LAYOUT, payload.inventoryLayout());
	}

	@Test
	void 여섯줄_설정은_확장_메뉴_구성을_광고한다() {
		SharedFateMod.config.mainInventoryRows = 6;

		HandshakePayload payload = HandshakePayload.current();

		assertEquals(HandshakePayload.SIX_ROW_LAYOUT, payload.inventoryLayout());
	}
}
