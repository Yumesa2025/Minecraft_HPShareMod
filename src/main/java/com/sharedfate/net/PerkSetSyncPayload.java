package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkSetType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C — 세트 효과의 <b>지금 상태</b>와, 툴팁에 「아직 안 가진 것」을 띄우는 데 필요한 목록.
 *
 * <h2>왜 클라이언트가 스스로 세지 못하는가</h2>
 * <p>클라이언트는 증강 풀을 아예 읽지 않는다 — {@code PerkRegistry.load} 를 부르는 자리는
 * {@code SharedFateMod} 한 곳뿐이고 그것은 서버 경로다. 보유 증강조차 id 없이
 * {@code (이름, 설명, 등급)} 문자열로만 받는다({@link PerkSyncPayload}). 그래서 「채굴을 몇 개
 * 가졌는가」도, 「채굴에 무엇이 더 있는가」도 서버가 내려보내야만 화면에 뜬다.
 *
 * <h2>왜 {@link PerkSyncPayload} 에 얹지 않았는가</h2>
 * <p>{@link PerkClientFeaturesPayload} 와 같은 이유다. 기존 레코드에 필드를 더하면 그 패킷을
 * 읽고 쓰는 모든 자리가 함께 바뀐다. 새 패킷을 하나 더 두면 잘못 건드렸을 때 무너지는 범위가
 * 이 패킷 안으로 갇힌다. 보내는 조건도 다르다 — 저쪽은 보유 목록이 바뀔 때, 이쪽은
 * <b>세트 판정 결과</b>가 바뀔 때다.
 *
 * <h2>상한을 두는 이유</h2>
 * <p>{@code ByteBufCodecs.list(max)} 는 상한을 넘는 목록을 만나면 인코딩·디코딩 양쪽에서
 * 예외를 던진다. 그 예외는 패킷 한 장이 아니라 <b>접속 자체</b>를 끊는다. 그래서 상한을
 * 코덱에만 맡기지 않고 생성자에서 먼저 잘라 둔다 — 세트 줄이 열두 개가 되는 날이 와도
 * 화면이 한 줄 덜 뜰 뿐 아무도 튕기지 않는다.
 *
 * @param sets    유형별 진행 상황. 유형마다 최대 한 줄이다
 * @param catalog 유형에 속한 증강의 이름표. 툴팁이 「아직 없는 것」을 고르는 데 쓴다
 */
public record PerkSetSyncPayload(List<SetLine> sets, List<CatalogEntry> catalog)
		implements CustomPacketPayload {

	/**
	 * 실을 수 있는 세트 줄의 개수 상한.
	 *
	 * <p>유형 개수와 같다. 숫자를 따로 적지 않고 {@link PerkSetType} 에서 세는 이유는, 유형이
	 * 늘었을 때 여기를 함께 고치는 것을 잊으면 <b>맨 뒤 유형이 조용히 사라지기</b> 때문이다.
	 */
	public static final int MAX_SETS = PerkSetType.values().length;

	/**
	 * 실을 수 있는 이름표의 개수 상한.
	 *
	 * <p>증강 하나가 유형을 여럿 가질 수 있어 이름표 수는 증강 수보다 많아진다. 지금 유형이
	 * 붙은 증강이 예순 몇 개라 두 배 넘는 여유를 둔 값이다.
	 *
	 * <p>넘칠 때는 <b>뒤에서부터</b> 버린다. 그래서 서버는 이 목록을 <b>유형별로 모아서</b>
	 * 실어야 한다 — 유형이 뒤섞여 있으면 잘린 뒤에 여러 유형의 툴팁이 조금씩 함께 빈다.
	 */
	public static final int MAX_CATALOG = 160;

	/** 세트가 하나도 없는 상태. 팀이 없거나 증강을 쓰지 않는 팀에 보낸다. */
	public static final PerkSetSyncPayload EMPTY = new PerkSetSyncPayload(List.of(), List.of());

	public static final Type<PerkSetSyncPayload> TYPE = new Type<>(SharedFateMod.id("perk_sets"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PerkSetSyncPayload> CODEC =
			StreamCodec.composite(
					SetLine.CODEC.apply(ByteBufCodecs.list(MAX_SETS)), PerkSetSyncPayload::sets,
					CatalogEntry.CODEC.apply(ByteBufCodecs.list(MAX_CATALOG)),
					PerkSetSyncPayload::catalog,
					PerkSetSyncPayload::new);

	public PerkSetSyncPayload {
		sets = trim(sets, MAX_SETS);
		catalog = trim(catalog, MAX_CATALOG);
	}

	/**
	 * 유형 하나의 진행 상황.
	 *
	 * <p>「채굴 2/3」은 {@code owned=2, nextThreshold=3} 이다. 임계값을 클라이언트가 다시 세지
	 * 않는 이유는 {@link PerkSetType#threshold()} 가 시뮬레이션 결과로 자주 바뀌는 값이라,
	 * 양쪽에 적어 두면 <b>버전이 다른 클라이언트가 다른 숫자를 그린다</b>는 것이다.
	 *
	 * @param typeId        {@link PerkSetType#id()}. 예: {@code mining}. 이름표를 고르는 열쇠다
	 * @param displayName   화면에 적을 한국어 이름. 예: {@code 채굴}
	 * @param owned         지금 가진 그 유형 증강의 개수
	 * @param nextThreshold 다음 단계에 필요한 개수. 더 오를 곳이 없으면 0
	 * @param activeTier    지금 켜져 있는 단계. 하나도 안 켜졌으면 0
	 */
	public record SetLine(String typeId, String displayName, int owned, int nextThreshold,
			int activeTier) {

		public SetLine {
			typeId = text(typeId);
			displayName = text(displayName);
			// VAR_INT 는 음수를 싣지 못한다. 판정이 어긋나도 접속이 끊기면 안 된다.
			owned = Math.max(0, owned);
			nextThreshold = Math.max(0, nextThreshold);
			activeTier = Math.max(0, activeTier);
		}

		/** 세트 효과가 이미 켜져 있는가. */
		public boolean active() {
			return activeTier > 0;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, SetLine> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, SetLine::typeId,
						ByteBufCodecs.STRING_UTF8, SetLine::displayName,
						ByteBufCodecs.VAR_INT, SetLine::owned,
						ByteBufCodecs.VAR_INT, SetLine::nextThreshold,
						ByteBufCodecs.VAR_INT, SetLine::activeTier,
						SetLine::new);
	}

	/**
	 * 유형에 속한 증강 하나의 이름표.
	 *
	 * <p>가진 것까지 함께 싣는다. 툴팁이 쓰는 것은 {@code owned == false} 인 것뿐이지만,
	 * <b>가진 것을 빼고 보내면 「전부 모았다」와 「목록이 잘렸다」를 구별할 수 없다.</b>
	 *
	 * <p>id 를 싣지 않는 이유는 클라이언트가 id 로 할 일이 없기 때문이다. 이름과 등급만 있으면
	 * 툴팁 한 줄이 나온다. 같은 이름이 둘 있어도 툴팁에 두 줄로 뜰 뿐 아무것도 깨지지 않는다.
	 *
	 * @param typeId   {@link PerkSetType#id()}. {@link SetLine#typeId()} 와 맞물린다
	 * @param perkName 증강 이름
	 * @param rarity   등급 문자열({@code silver} / {@code gold} / {@code prism}). 글자색이 된다
	 * @param owned    이미 가진 증강인가
	 */
	public record CatalogEntry(String typeId, String perkName, String rarity, boolean owned) {

		public CatalogEntry {
			typeId = text(typeId);
			perkName = text(perkName);
			rarity = text(rarity);
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, CatalogEntry> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, CatalogEntry::typeId,
						ByteBufCodecs.STRING_UTF8, CatalogEntry::perkName,
						ByteBufCodecs.STRING_UTF8, CatalogEntry::rarity,
						ByteBufCodecs.BOOL, CatalogEntry::owned,
						CatalogEntry::new);
	}

	/** 목록을 상한까지만 남기고 자른다. 언제나 바꿀 수 없는 목록을 돌려준다. */
	private static <T> List<T> trim(List<T> values, int limit) {
		if (values == null) {
			return List.of();
		}
		if (values.size() <= limit) {
			return List.copyOf(values);
		}
		return List.copyOf(new ArrayList<>(values.subList(0, limit)));
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
