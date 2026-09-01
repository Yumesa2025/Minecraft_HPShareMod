package com.sharedfate.client.mixin;

import com.sharedfate.client.ClientTeamState;
import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.inventory.ExpandedMenuLayout;
import com.sharedfate.inventory.SelfPaintedSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 추가 27칸을 <b>모든 창에서 플레이어 인벤토리 바로 아래</b>에 그립니다.
 *
 * <h2>왜 창 안으로 들여왔는가</h2>
 *
 * <p>예전에는 창 <b>오른쪽 바깥</b>에 세로로 붙였습니다. 바닐라
 * {@code AbstractContainerScreen.mouseClicked} 는 누른 자리가 창 넓이·높이 밖이면
 * <b>가리키는 칸이 있어도 무시하고</b> 슬롯 번호를 -999(= 버리기)로 덮어씁니다. 그래서
 * 추가 칸을 누르면 들고 있던 아이템이 바닥에 떨어졌습니다.
 *
 * <p>{@code hasClickedOutside} 를 가로채 막아 두었지만 그것으로는 부족했습니다 —
 * 화로·제작대처럼 {@code AbstractRecipeBookScreen} 을 물려받은 화면은 그 메서드를
 * <b>덮어쓰기</b> 때문에 위 클래스에 넣은 가로채기가 아예 돌지 않습니다.
 *
 * <p>지금은 칸이 창 <b>안</b>에 있고 창 높이도 그만큼 커졌으므로, 바닐라가 스스로
 * 「창 안」이라고 판정합니다. 막을 것이 없어졌습니다.
 *
 * <h2>배경은 바닐라 인벤토리 그림을 잘라 씁니다</h2>
 *
 * <p>창마다 배경 그림이 다르지만 <b>아래쪽 인벤토리 부분은 모두 같은 모양</b>입니다.
 * 그래서 창이 제 배경을 다 그린 뒤에 {@code inventory.png} 에서 「세 줄」과 「칸막이 +
 * 핫바」를 잘라 덮습니다. 창마다 코드를 따로 둘 필요가 없습니다.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
	private static final int RED = 0xFFFF3030;
	/**
	 * 추가 슬롯 칸 색. 바닐라 인벤토리 칸과 같아야 한다.
	 *
	 * <p>바닐라 텍스처의 슬롯은 <b>테두리가 어둡고 안쪽이 밝은 회색</b>이다. 예전에는 이 둘이
	 * 뒤집혀 있어서 추가 칸만 유독 어둡게 보였고, 같은 공유 인벤토리인데 두 종류처럼 읽혔다.
	 */
	private static final int EXTRA_SLOT_BORDER = 0xFF373737;
	private static final int EXTRA_SLOT_INNER = 0xFF8B8B8B;

	/** 인벤토리 첫 줄 y 에서 그림 띠의 위쪽까지. 칸은 띠보다 1px 아래에서 시작한다. */
	@Unique
	private static final int BAND_OFFSET = ExpandedInventoryManager.EXTRA_PANEL_HEIGHT - 1;
	/** {@code inventory.png} 에서 인벤토리 한 줄이 있는 자리. */
	@Unique
	private static final int ROW_SOURCE_Y = 119;
	/** 인벤토리와 핫바 사이 칸막이. */
	@Unique
	private static final int SEPARATOR_SOURCE_Y = 137;
	@Unique
	private static final int SEPARATOR_HEIGHT = 4;
	/** 핫바 띠. */
	@Unique
	private static final int HOTBAR_SOURCE_Y = 141;
	@Unique
	private static final int HOTBAR_HEIGHT = 25;
	/** 바닐라 인벤토리 그림의 넓이. */
	@Unique
	private static final int PANEL_WIDTH = 176;
	@Unique
	private static final int TEXTURE_SIZE = 256;

	@Unique
	private boolean sharedfate$baseImageHeightKnown;
	@Unique
	private int sharedfate$baseImageHeight;

	@Inject(method = "init", at = @At("HEAD"))
	private void sharedfate$prepareExpandedLayout(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && ExpandedInventoryManager.enabled()) {
			ExpandedInventoryManager.setClientTeamActive(client.player, ClientTeamState.inTeam());
		}
		sharedfate$applyImageHeight();
	}

	/** 회차 중에 팀에 들어가거나 나가면 창 높이가 곧바로 따라가야 합니다. */
	@Inject(method = "containerTick", at = @At("HEAD"))
	private void sharedfate$followTeamChanges(CallbackInfo ci) {
		if (sharedfate$applyImageHeight()) {
			Screen self = (Screen) (Object) this;
			self.resize(self.width, self.height);
		}
	}

	@Inject(method = "extractSlots", at = @At("HEAD"))
	private void sharedfate$drawExtraPanel(
			GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		int inventoryTop = sharedfate$expandedInventoryTop();
		if (inventoryTop < 0) {
			return;
		}
		// 인벤토리가 창 왼쪽에 붙어 있지 않은 창도 있습니다(주민 거래 등). 추가 첫 칸의
		// x 에서 되짚어 인벤토리 그림의 왼쪽 끝을 구합니다.
		int panelLeft = sharedfate$extraSlot().x - 8;
		int bandTop = inventoryTop + BAND_OFFSET;
		for (int row = 0; row < ExpandedInventoryManager.EXTRA_ROWS; row++) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, AbstractContainerScreen.INVENTORY_LOCATION,
					panelLeft, bandTop + row * ExpandedInventoryManager.SLOT_PITCH,
					0.0F, (float) ROW_SOURCE_Y,
					PANEL_WIDTH, ExpandedInventoryManager.SLOT_PITCH,
					TEXTURE_SIZE, TEXTURE_SIZE);
		}
		int separatorTop = bandTop + ExpandedInventoryManager.EXTRA_PANEL_HEIGHT;
		graphics.blit(RenderPipelines.GUI_TEXTURED, AbstractContainerScreen.INVENTORY_LOCATION,
				panelLeft, separatorTop, 0.0F, (float) SEPARATOR_SOURCE_Y,
				PANEL_WIDTH, SEPARATOR_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
		graphics.blit(RenderPipelines.GUI_TEXTURED, AbstractContainerScreen.INVENTORY_LOCATION,
				panelLeft, separatorTop + SEPARATOR_HEIGHT, 0.0F, (float) HOTBAR_SOURCE_Y,
				PANEL_WIDTH, HOTBAR_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
	}

	/** 추가 첫 칸. {@link #sharedfate$expandedInventoryTop()} 가 0 이상일 때만 부릅니다. */
	@Unique
	private Slot sharedfate$extraSlot() {
		AbstractContainerMenu menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
		return menu.getSlot(((ExpandedMenuLayout) menu).sharedfate$extraSlotStart());
	}

	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void sharedfate$drawExtraSlotBackground(GuiGraphicsExtractor graphics, Slot slot,
			int mouseX, int mouseY, CallbackInfo ci) {
		// 크리에이티브 화면은 슬롯을 SlotWrapper 로 한 겹 감싸므로 그릇으로도 알아봅니다.
		boolean painted = slot instanceof SelfPaintedSlot
				|| slot.container instanceof ExpandedInventoryContainer;
		if (painted && slot.isActive()) {
			graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, EXTRA_SLOT_BORDER);
			graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, EXTRA_SLOT_INNER);
		}
	}

	@Inject(method = "extractSlot", at = @At("RETURN"))
	private void sharedfate$markAllySlot(GuiGraphicsExtractor graphics, Slot slot,
			int mouseX, int mouseY, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (!ClientTeamState.inTeam() || client.player == null
				|| slot.container != client.player.getInventory()) {
			return;
		}
		int hotbarSlot = slot.index;
		if ((Object) this instanceof CreativeModeInventoryScreen
				&& hotbarSlot >= 36 && hotbarSlot < 45) {
			hotbarSlot -= 36;
		}
		if (hotbarSlot >= 0 && hotbarSlot < 9
				&& ClientTeamState.isAllyUsingHotbarSlot(hotbarSlot)) {
			graphics.outline(slot.x - 1, slot.y - 1, 18, 18, RED);
		}
	}

	/**
	 * 창 높이를 지금 상태에 맞춥니다.
	 *
	 * @return 높이가 바뀌었으면 참
	 */
	@Unique
	private boolean sharedfate$applyImageHeight() {
		AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
		if (!sharedfate$baseImageHeightKnown) {
			sharedfate$baseImageHeight = screen.sharedfate$getImageHeight();
			sharedfate$baseImageHeightKnown = true;
		}
		int wanted = sharedfate$baseImageHeight
				+ (sharedfate$expandedInventoryTop() >= 0
						? ExpandedInventoryManager.EXTRA_PANEL_HEIGHT : 0);
		if (screen.sharedfate$getImageHeight() == wanted) {
			return false;
		}
		screen.sharedfate$setImageHeight(wanted);
		return true;
	}

	/**
	 * 추가 칸이 지금 보이고 있으면 인벤토리 첫 줄의 y 를, 아니면 -1 을 돌려줍니다.
	 *
	 * <p>크리에이티브 화면은 탭 구조가 달라 옆에 따로 붙이므로 여기서 뺍니다.
	 */
	@Unique
	private int sharedfate$expandedInventoryTop() {
		if ((Object) this instanceof CreativeModeInventoryScreen) {
			return -1;
		}
		AbstractContainerMenu menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
		if (!(menu instanceof ExpandedMenuLayout layout)) {
			return -1;
		}
		int extraStart = layout.sharedfate$extraSlotStart();
		int inventoryTop = layout.sharedfate$inventoryTopY();
		if (extraStart < 0 || inventoryTop < 0
				|| extraStart + ExpandedInventoryManager.EXTRA_SIZE > menu.slots.size()) {
			return -1;
		}
		return menu.getSlot(extraStart).isActive() ? inventoryTop : -1;
	}
}
