package com.sharedfate.client.perk;

import com.sharedfate.net.PerkSetSyncPayload;
import com.sharedfate.ui.PerkSetLines;
import com.sharedfate.ui.PerkSetTooltip;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 클라이언트가 보관하는 "지금 세트가 어디까지 왔는가".
 *
 * <p>{@link PerkSetSyncPayload} 로 갱신되며, 월드에서 나가면 {@link #clear()} 로 비운다.
 * {@link ClientPerkFeatures} 는 <b>동작을 가르는 판단값</b>이고 이쪽은 <b>화면에 뿌릴
 * 표시용</b>이다. 이 캐시를 보고 무엇이 켜졌는지 판단해 클라이언트가 무엇을 하는 일은 없다.
 * 세트 효과를 실제로 거는 것은 언제나 서버다.
 *
 * <h2>서버가 말해 주기 전에는 비어 있다</h2>
 * <p>기본값은 빈 목록이다. 서버가 이 모드를 안 쓰거나 증강을 안 쓰는 팀이면 패킷이 오지 않고,
 * 그러면 HUD 에도 팀 화면에도 세트 줄이 한 줄도 안 뜬다. 클라이언트는 증강 풀을 읽지 않으므로
 * <b>스스로 채울 방법이 아예 없다</b> — 그래서 「모르면 안 그린다」가 유일하게 옳은 기본값이다.
 *
 * <p>값을 읽는 자리가 렌더 스레드({@code HudElement.extractRenderState},
 * {@code Screen.extractRenderState})이고 쓰는 자리가 패킷 수신인데, 수신 쪽을
 * {@code client.execute(...)} 로 클라이언트 본 스레드에 올려 두었으므로 둘은 같은 스레드다.
 */
public final class ClientPerkSets {
	private static List<PerkSetLines.Entry> sets = List.of();
	private static List<PerkSetTooltip.TierEntry> tiers = List.of();
	private static List<PerkSetTooltip.Entry> catalog = List.of();

	private ClientPerkSets() {
	}

	/**
	 * {@link PerkSetSyncPayload} 를 받았을 때 부른다.
	 *
	 * <p>패킷 레코드를 그대로 들고 있지 않고 {@code com.sharedfate.ui} 의 레코드로 옮겨 담는다.
	 * 화면 계산을 하는 자리가 마인크래프트 네트워크 클래스를 몰라도 되게 하려는 것이다.
	 */
	public static void update(@Nullable PerkSetSyncPayload payload) {
		if (payload == null) {
			clear();
			return;
		}
		List<PerkSetLines.Entry> nextSets = new ArrayList<>(payload.sets().size());
		for (PerkSetSyncPayload.SetLine line : payload.sets()) {
			nextSets.add(new PerkSetLines.Entry(line.typeId(), line.displayName(),
					line.owned(), line.nextThreshold(), line.activeTier(), line.intervalTicks(),
					line.anchorTick()));
		}
		List<PerkSetTooltip.TierEntry> nextTiers = new ArrayList<>(payload.tiers().size());
		for (PerkSetSyncPayload.TierLine line : payload.tiers()) {
			nextTiers.add(new PerkSetTooltip.TierEntry(line.typeId(), line.count(),
					line.description(), line.active()));
		}
		List<PerkSetTooltip.Entry> nextCatalog = new ArrayList<>(payload.catalog().size());
		for (PerkSetSyncPayload.CatalogEntry entry : payload.catalog()) {
			nextCatalog.add(new PerkSetTooltip.Entry(entry.typeId(), entry.perkName(),
					entry.rarity(), entry.owned()));
		}
		sets = List.copyOf(nextSets);
		tiers = List.copyOf(nextTiers);
		catalog = List.copyOf(nextCatalog);
	}

	/** 서버가 보낸 그대로의 유형 목록. 가진 것이 0인 유형도 들어 있다. */
	public static List<PerkSetLines.Entry> all() {
		return sets;
	}

	/** 화면에 실제로 그릴 줄들. 가진 것이 없는 유형은 빠지고 켜진 것이 위로 온다. */
	public static List<PerkSetLines.Line> lines(int limit) {
		return PerkSetLines.visible(sets, limit);
	}

	/**
	 * HUD 가 그릴 줄들. 「보급」 줄에는 다음 보급까지 남은 시간이 붙는다.
	 *
	 * <p>팀 화면·선택창 곁판은 시계가 붙지 않는 {@link #lines(int)} 를 쓴다. 줄 폭이 매초
	 * 흔들리면 마우스가 어느 줄 위인지 재는 계산까지 함께 흔들린다.
	 *
	 * @param gameTime 지금 게임 시간. 바닐라가 초마다 서버 값으로 맞춰 준다
	 */
	public static List<PerkSetLines.Line> hudLines(int limit, long gameTime) {
		return PerkSetLines.visible(sets, limit, gameTime);
	}

	/** 그릴 줄이 하나라도 있는가. 없으면 화면은 세트 자리를 아예 비워 둔다. */
	public static boolean hasLines() {
		return !lines(PerkSetLines.MAX_HUD_LINES).isEmpty();
	}

	/** 이 유형에서 아직 안 가진 증강들. 툴팁이 쓴다. */
	public static List<PerkSetTooltip.Missing> missing(String typeId) {
		return PerkSetTooltip.missingOf(catalog, typeId);
	}

	/**
	 * 유형 하나의 툴팁 본문. <b>선택 카드와 팀 화면이 함께 쓴다.</b>
	 *
	 * <p>진행도 줄과 단계 설명과 「아직 없는 것」이 한 덩어리로 들어 있다. 화면은 색만 입혀
	 * 그리면 된다.
	 *
	 * @param typeId 유형 id. 모르는 값이면 빈 덩어리가 나온다
	 */
	public static PerkSetTooltip.Body tooltip(String typeId) {
		PerkSetLines.Entry found = null;
		for (PerkSetLines.Entry entry : sets) {
			if (entry != null && entry.typeId().equals(typeId)) {
				found = entry;
				break;
			}
		}
		if (found == null) {
			return new PerkSetTooltip.Body("", List.of(), new PerkSetTooltip.Trimmed(List.of(), 0));
		}
		return PerkSetTooltip.describe(typeId, found.displayName(), found.owned(),
				found.nextThreshold(), tiers, catalog, PerkSetTooltip.MAX_ROWS);
	}

	/** 서버가 보낸 단계 전부. 유형을 안 가려서 쓸 일은 드물다. */
	public static List<PerkSetTooltip.TierEntry> allTiers() {
		return tiers;
	}

	/** 이 유형의 한국어 이름. 모르는 유형이면 id 를 그대로 돌려준다. */
	public static String displayName(String typeId) {
		for (PerkSetLines.Entry entry : sets) {
			if (entry.typeId().equals(typeId)) {
				return entry.displayName();
			}
		}
		return typeId == null ? "" : typeId;
	}

	/**
	 * 세트 상태의 요약.
	 *
	 * <p>{@code TeamScreen.signature()} 가 이 값을 물고 있다가 달라졌을 때만 배치를 다시 잡는다.
	 * <b>여기에 담기지 않은 변화는 화면에 영영 나타나지 않는다.</b> 그래서 줄 수뿐 아니라
	 * 줄의 내용까지 담는다 — 「채굴 2/3」이 「채굴 3/3」이 되는 것은 줄 수가 그대로다.
	 *
	 * <p>이름표 목록은 담지 않는다. 그것은 툴팁 내용일 뿐 <b>위젯 구성을 바꾸지 않고</b>,
	 * 백여 줄의 문자열을 매 틱 이어 붙이는 값이 되기 때문이다.
	 */
	public static String signature() {
		StringBuilder summary = new StringBuilder();
		for (PerkSetLines.Entry entry : sets) {
			summary.append(entry.typeId()).append(entry.owned()).append('/')
					.append(entry.nextThreshold()).append('@').append(entry.activeTier())
					.append(';');
		}
		return summary.toString();
	}

	/** 월드에서 나갈 때 부른다. */
	public static void clear() {
		sets = List.of();
		tiers = List.of();
		catalog = List.of();
	}
}
