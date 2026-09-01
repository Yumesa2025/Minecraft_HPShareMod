package com.sharedfate.client.perk;

import com.sharedfate.net.PerkDrawPayload;
import com.sharedfate.ui.PerkDrawRoll;
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
 * <h2>언제 넘기고 언제 멈출지는 {@link PerkDrawRoll} 이 정한다</h2>
 * <p>이 화면은 그 판단을 그리고 소리로 옮기기만 한다. 굴림이 점점 느려지는 이유, 총 길이를
 * 굴림과 결과 표시로 나눈 이유, 결과가 정확히 한 번만 알려지는 근거가 모두 그쪽에 있다.
 *
 * <h2>길이는 서버가 정한다</h2>
 * <p>연출이 도는 동안 서버는 시간을 멈춰 두므로 두 길이가 어긋나면 안 된다. 그래서 총
 * 시간은 {@code PerkChoiceSession.DRAW_TICKS} 가 실려 오는 대로 쓴다. 그 안을 어떻게 쪼갤지만
 * 클라이언트가 정한다 — <b>총 길이는 1틱도 늘지 않는다.</b>
 *
 * <p>이 창이 떠 있는 동안 서버는 시간을 멈추고 무적을 걸어 둔다. 총 시간이 지나면 서버가
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

	private final List<String> names;
	private final String chooserName;
	private final PerkDrawRoll roll;

	private int shownIndex;

	public PerkDrawScreen(PerkDrawPayload payload) {
		super(Component.literal("증강 선택자 추첨"));
		List<String> pool = new ArrayList<>(payload.memberNames());
		if (pool.isEmpty()) {
			pool.add(payload.chooserName());
		}
		this.names = List.copyOf(pool);
		this.chooserName = payload.chooserName();
		this.roll = new PerkDrawRoll(payload.durationTicks());
	}

	/**
	 * 굴림을 한 틱 진행시키고 그 결과를 화면과 소리로 옮긴다.
	 *
	 * <p>판단은 전부 {@link PerkDrawRoll} 이 한다. 여기서 되묻거나 조건을 더 얹으면 예전처럼
	 * <b>어떤 틱에서는 소리가 통째로 건너뛰어지는</b> 고장이 다시 들어온다.
	 */
	@Override
	public void tick() {
		switch (roll.tick()) {
			case NEXT_NAME -> {
				shownIndex = (shownIndex + 1) % names.size();
				playTickSound();
			}
			case REVEAL -> playPickedSound();
			case NOTHING -> {
			}
		}
	}

	private boolean finished() {
		return roll.revealed();
	}

	/** 굴러가는 동안의 딸깍 소리. 끝으로 갈수록 음이 올라간다. */
	private void playTickSound() {
		if (this.minecraft == null) {
			return;
		}
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
				SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F + roll.progress() * 0.6F, 0.5F));
	}

	/**
	 * 멈춘 순간의 소리. 딸깍과 확실히 달라야 "정해졌다"로 읽힌다.
	 *
	 * <p>굴림이 끝나는 그 틱에 화면도 뽑힌 이름으로 바뀌므로({@link #finished()} 가 같은
	 * {@link PerkDrawRoll} 을 본다) 소리와 그림이 어긋날 수 없다.
	 */
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
