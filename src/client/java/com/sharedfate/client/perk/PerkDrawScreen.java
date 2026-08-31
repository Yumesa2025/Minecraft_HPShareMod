package com.sharedfate.client.perk;

import com.sharedfate.net.PerkDrawPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 누가 증강을 고를지 뽑는 3.5초짜리 연출.
 *
 * <p>이름을 빠르게 굴리다 점점 느려지고, 마지막에 서버가 정해 둔 사람에서 멈춘다.
 * <b>뽑기 자체는 서버가 이미 끝냈다.</b> 클라이언트가 굴리면 사람마다 다른 결과가 보이므로
 * 여기서는 연출만 한다.
 *
 * <h2>왜 점점 느려지는가</h2>
 * <p>일정한 속도로 굴리다 뚝 멈추면 "정해진 답을 보여 줬을 뿐"으로 읽힌다. 뒤로 갈수록
 * 간격을 벌리면 마지막 한두 번에 시선이 머물러, 멈추는 순간이 사건처럼 보인다.
 *
 * <h2>길이는 서버가 정한다</h2>
 * <p>연출이 도는 동안 서버는 시간을 멈춰 두므로 두 길이가 어긋나면 안 된다. 그래서 총
 * 시간은 {@code PerkChoiceSession.DRAW_TICKS} 가 실려 오는 대로 쓰고, 여기서는 그 안에서
 * 몇 번 굴릴지만 정한다.
 *
 * <p>이 창이 떠 있는 동안 서버는 시간을 멈추고 무적을 걸어 둔다. 연출이 끝나면 서버가
 * 선택창을 보내며 이 화면을 밀어낸다. ESC 로는 닫을 수 없다.
 */
public class PerkDrawScreen extends Screen {
	private static final int TEXT_MAIN = 0xFFE8E8F0;
	private static final int TEXT_DIM = 0xFF9AA0AA;
	private static final int NAME_ROLLING = 0xFFFFD24A;
	private static final int NAME_PICKED = 0xFF80FF20;
	private static final int PANEL_BG = 0xC8101018;

	/** 이름을 확대해 그릴 배율. */
	private static final int NAME_SCALE = 2;
	/** 굴림이 끝나고 결과를 강조하는 데 쓰는 살짝 큰 배율. */
	private static final int PICKED_SCALE = 3;

	/**
	 * 처음 간격(틱)과 마지막 간격(틱). 뒤로 갈수록 이 사이를 오간다.
	 *
	 * <p>연출 전체를 5초에서 3.5초로 30% 줄이면서 마지막 간격도 12틱에서 8틱으로 함께
	 * 줄였다. 총 시간만 줄이면 뒤쪽 한 칸이 전체의 17%를 차지하게 되어 <b>끝에서만 유난히
	 * 길게 늘어지는</b> 연출이 된다. 둘을 같은 비율로 줄이면 굴리는 횟수가 그대로라
	 * 리듬은 같고 속도만 빨라진다.
	 *
	 * <p>처음 간격은 2틱 그대로 둔다. 초당 10번은 이미 사람이 글자를 읽어낼 수 있는
	 * 한계라, 1틱으로 줄이면 빨라지는 것이 아니라 그냥 뭉개진다.
	 */
	private static final int FIRST_STEP_TICKS = 2;
	private static final int LAST_STEP_TICKS = 8;

	private final List<String> names;
	private final String chooserName;
	private final int totalTicks;

	private int elapsedTicks;
	private int shownIndex;
	private int ticksUntilNextName = FIRST_STEP_TICKS;

	public PerkDrawScreen(PerkDrawPayload payload) {
		super(Component.literal("증강 선택자 추첨"));
		List<String> pool = new ArrayList<>(payload.memberNames());
		if (pool.isEmpty()) {
			pool.add(payload.chooserName());
		}
		this.names = List.copyOf(pool);
		this.chooserName = payload.chooserName();
		this.totalTicks = Math.max(1, payload.durationTicks());
	}

	@Override
	public void tick() {
		if (elapsedTicks >= totalTicks) {
			return;
		}
		elapsedTicks++;
		if (--ticksUntilNextName > 0) {
			return;
		}
		shownIndex = (shownIndex + 1) % names.size();
		ticksUntilNextName = stepTicks();
		playTickSound();
		if (finished()) {
			playPickedSound();
		}
	}

	/** 지금 시점의 이름 교체 간격. 끝으로 갈수록 길어진다. */
	private int stepTicks() {
		float progress = Math.min(1.0F, (float) elapsedTicks / totalTicks);
		return Math.round(FIRST_STEP_TICKS + (LAST_STEP_TICKS - FIRST_STEP_TICKS) * progress);
	}

	private boolean finished() {
		return elapsedTicks >= totalTicks;
	}

	/** 굴러가는 동안의 딸깍 소리. 끝으로 갈수록 음이 올라간다. */
	private void playTickSound() {
		if (this.minecraft == null || finished()) {
			return;
		}
		float progress = Math.min(1.0F, (float) elapsedTicks / totalTicks);
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
				SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F + progress * 0.6F, 0.5F));
	}

	/** 멈춘 순간의 소리. 딸깍과 확실히 달라야 "정해졌다"로 읽힌다. */
	private void playPickedSound() {
		if (this.minecraft == null) {
			return;
		}
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
				SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F, 0.9F));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		int centerX = this.width / 2;
		int centerY = this.height / 2;

		graphics.fill(0, centerY - 44, this.width, centerY + 34, PANEL_BG);
		graphics.centeredText(this.font, this.title, centerX, centerY - 36, TEXT_MAIN);

		boolean done = finished();
		String shown = done ? chooserName : names.get(shownIndex);
		int color = done ? NAME_PICKED : NAME_ROLLING;
		int scale = done ? PICKED_SCALE : NAME_SCALE;

		// 글자 확대는 pose 에 배율을 쌓아서 한다. PerkOfferScreen 의 카운트다운과 같은 방식이다.
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, centerY - 12);
		pose.scale(scale, scale);
		graphics.centeredText(this.font, Component.literal(shown), 0, 0, color);
		pose.popMatrix();

		graphics.centeredText(this.font, done
						? Component.literal(chooserName + "님이 고릅니다").withStyle(ChatFormatting.BOLD)
						: Component.literal("누가 고를지 정하는 중..."),
				centerX, centerY + 18, done ? TEXT_MAIN : TEXT_DIM);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/** 서버가 선택창으로 바꿔 줄 때까지 닫을 수 없다. */
	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	/** 시간은 서버가 이미 멈춰 두었다. 클라이언트까지 일시정지 화면으로 만들 이유가 없다. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
