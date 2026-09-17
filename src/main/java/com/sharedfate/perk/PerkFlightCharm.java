package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.perk.effect.FlightCharmEffect;
import com.sharedfate.sync.TitleMessenger;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code flight_charm} 증강(프리즘 「비행 부적」)의 집행부.
 *
 * <p>부적을 주 손에 들고 <b>허공을</b> 우클릭하면 정의에 적힌 시간만큼 하늘을 날 수 있고,
 * 그다음 쿨타임이 걸린다. 부적은 공유 인벤토리의 핫바 1번 칸
 * ({@link FlightCharmEffect#LOCKED_SLOT})에 고정되어 꺼낼 수도 옮길 수도 버릴 수도 없다.
 *
 * <h2>등록 지점</h2>
 * <p>{@code UseItemCallback.EVENT} 에 붙는다. {@link PerkDiamondSundial}·{@link PerkRallyShard}·
 * {@link PerkOreExchange} 와 같은 자리이고, 넷 다 자기 아이템이 아니면 {@code PASS} 를
 * 돌려주므로 서로 부딪히지 않는다. 팬텀 막은 바닐라에서 허공 우클릭에 아무 동작이 없어
 * 가로채도 잃는 것이 없다.
 *
 * <h2>통신 규약을 올리지 않는다</h2>
 * <p>비행 허가를 알리는 {@code ClientboundPlayerAbilitiesPacket}(= {@code onUpdateAbilities}),
 * 액션바, 소리, 쿨타임 게이지가 전부 바닐라 패킷이다. 모드를 깔지 않은 클라이언트도 그대로
 * 날고 그대로 본다.
 *
 * <h2>{@code mayfly} 는 반드시 되돌린다</h2>
 * <p>26.2 의 칸 이름은 {@code Abilities.mayfly} 다(소문자 f). 이 칸은 플레이어 저장 자료에
 * 그대로 적히므로, 켠 채로 접속이 끊기면 <b>다음 접속에도 날 수 있는 영구 비행</b>이 된다.
 * 그래서 두 겹으로 막는다.
 *
 * <ol>
 *   <li><b>기억했다 되돌린다</b> — 켜기 전 값을 {@link Session#grantedByUs} 에 남긴다.
 *       크리에이티브·관전 모드는 원래 {@code mayfly} 가 참이므로 그런 사람에게는 아예 켜지
 *       않고({@link #handle} 이 거절한다) 되돌릴 것도 없다. 되돌릴 때도 지금 크리에이티브·관전
 *       모드면 손대지 않는다 — 비행 도중 관리자가 모드를 바꾼 경우에 남의 비행을 빼앗지
 *       않기 위해서다.</li>
 *   <li><b>남았으면 지운다</b> — 접속할 때 {@link #onPlayerJoin} 이, 팀원인데 생존·모험 모드이고
 *       진행 중인 비행도 없는데 {@code mayfly} 가 참이면 그것을 끈다. 바닐라는 생존·모험
 *       모드에서 이 칸을 참으로 만들지 않으므로, 참으로 남아 있다는 것은 우리가 흘린 것이다.</li>
 * </ol>
 *
 * <p>걷어내는 자리는 모두 다섯이다 — 시간 만료·접속 종료({@link #onPlayerLeave})·사망과
 * 부활({@link #onRespawn} 과 {@link #tick} 의 사망 검사)·증강 상실({@link #tick})·서버 종료
 * ({@link #reset}). 차원 이동은 걷어내지 않고 <b>다시 켠다</b> — {@link #tick} 이 진행 중인
 * 비행의 {@code mayfly} 가 꺼져 있으면 도로 켜고 클라이언트에 다시 알린다.
 *
 * <h2>부적을 잃었을 때는 다시 준다</h2>
 * <p>{@link #tick} 이 주기마다 공유 인벤토리의 1번 칸을 본다. 증강을 가진 팀인데 그 칸에
 * 부적이 없으면 <b>새로 하나 만들어 되돌려 놓는다.</b> 죽어서 떨어뜨렸든 회차가 넘어가 인벤토리가
 * 비었든 같다. 같은 점검이 1번 칸 밖의 부적을 전부 지우므로 <b>부적은 언제나 팀에 하나뿐</b>이고,
 * 되돌려 놓기가 개수를 불리지 않는다. 이것은 「지급」이 아니라 「제자리로 돌리기」라, 지급을
 * {@link PerkGrantChain#run} 한 곳으로 모으는 규칙과 부딪히지 않는다 —
 * {@link #grantOnChoice} 는 여전히 그 한 곳에서만 불린다.
 *
 * <h2>칸을 지나지 않는 두 길도 이 점검이 막는다</h2>
 * <p>화면을 연 채 하는 조작은 {@code com.sharedfate.mixin.SlotFlightCharmLockMixin} 이 모두
 * 막지만, 두 길은 {@code Slot} 을 아예 지나지 않아 그 잠금이 닿지 않는다.
 *
 * <ul>
 *   <li><b>Q 키(화면을 닫은 채 버리기)</b> — {@code ServerPlayer.drop(boolean)} 이
 *       {@code Inventory.removeFromSelected(boolean)} 로 곧장 뽑아 간다.</li>
 *   <li><b>F 키(손 바꾸기)</b> — {@code setItemInHand} 두 번으로 끝난다.</li>
 * </ul>
 *
 * <p>둘 다 {@link #sweepTeam} 이 {@value #SWEEP_INTERVAL_TICKS} 틱 안에 되돌린다. 버린 부적은
 * 땅에 남지만 그것을 주우면 1번 칸 밖의 부적이라 다음 점검에서 사라지므로, 팀이 가진 부적은
 * 언제나 하나다.
 */
public final class PerkFlightCharm {
	/** 액션바·채팅 머리말. 다른 증강 알림과 같은 모양으로 맞춘다. */
	public static final String PREFIX = "[증강] ";

	/**
	 * 부적 자리를 점검하는 주기(틱).
	 *
	 * <p>Q 키로 버린 부적이 이 시간 안에 제자리로 돌아온다. 0.25초면 사람이 「버려졌다」고
	 * 느끼기 전이고, 팀 하나당 36칸을 훑는 비용도 4틱에 한 번으로 줄어든다.
	 */
	public static final int SWEEP_INTERVAL_TICKS = 5;

	/** 남은 시간을 액션바에 다시 적는 주기(틱). */
	private static final int ACTION_BAR_INTERVAL_TICKS = 20;

	private static final int TICKS_PER_SECOND = 20;

	/** 지금 날고 있는 사람들. 키는 플레이어 uuid 다. */
	private static final Map<UUID, Session> ACTIVE = new HashMap<>();

	private static int sweepCounter;

	private static volatile boolean warned;

	private PerkFlightCharm() {
	}

	/**
	 * 한 사람의 비행 한 판.
	 *
	 * @param grantedByUs 우리가 {@code mayfly} 를 거짓에서 참으로 바꿨는가. 거짓이면 되돌릴 때
	 *                    아무것도 건드리지 않는다
	 */
	private static final class Session {
		private int remainingTicks;
		private final boolean grantedByUs;
		/** 비행이 끝날 때 걸 쿨타임(틱). 쓰는 순간의 정의 값을 그대로 들고 간다. */
		private final int cooldownTicks;
		/**
		 * 쿨타임을 걸 때 넘길 묶음.
		 *
		 * <p>{@code ItemCooldowns} 는 {@code minecraft:use_cooldown} 의 <b>묶음 이름</b>만 읽으므로
		 * 쓰던 묶음의 사본이면 충분하다. 인벤토리의 실물을 들고 있으면 그 사이에 부적이
		 * 옮겨지거나 사라졌을 때 엉뚱한 것에 걸린다.
		 */
		private final ItemStack cooldownKey;

		private Session(int remainingTicks, boolean grantedByUs, int cooldownTicks,
				ItemStack cooldownKey) {
			this.remainingTicks = remainingTicks;
			this.grantedByUs = grantedByUs;
			this.cooldownTicks = cooldownTicks;
			this.cooldownKey = cooldownKey;
		}
	}

	// ------------------------------------------------------------------ 지급

	/**
	 * 증강 하나가 가진 {@code flight_charm} 효과의 부적을 지급한다.
	 *
	 * <p>{@link PerkRallyShard#grantOnChoice} 와 같은 자리·같은 규칙이다. 부르는 곳은
	 * {@link PerkGrantChain#run} 하나뿐이다 — {@code PerkEffect.apply} 에서 주면 접속할 때마다
	 * 부적이 늘어난다.
	 *
	 * <p>다른 지급과 달리 넘침 목록에 얹지 않고 <b>핫바 1번 칸에 직접 꽂는다.</b> 넘침 목록은
	 * 빈 칸을 앞에서부터 찾아 채우므로 1번 칸이 차 있으면 엉뚱한 곳에 놓인다. 원래 그 칸에
	 * 있던 것은 넘침 목록으로 밀어 다른 빈 칸으로 보낸다.
	 *
	 * @return 실제로 지급한 개수. 줄 것이 없었으면 0
	 */
	public static int grantOnChoice(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			@Nullable TeamState state, @Nullable Perk perk) {
		if (state == null || perk == null) {
			return 0;
		}

		int granted = 0;
		for (PerkEffect effect : perk.effects()) {
			if (!(effect instanceof FlightCharmEffect charm)) {
				continue;
			}
			try {
				ItemStack stack = charm.createItem();
				if (stack == null || stack.isEmpty()) {
					continue;
				}
				// 이미 부적을 가진 팀에 두 번째를 꽂지 않는다. 「환골탈태」로 같은 증강을 다시
				// 받는 길이 있어, 여기서 막지 않으면 1번 칸의 부적이 넘침 목록으로 밀려난다.
				if (holdsCharm(state)) {
					continue;
				}
				placeInLockedSlot(state, stack);
				granted++;
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("증강 '{}' 의 비행 부적 지급에 실패했습니다.", perk.id(), error);
			}
		}
		if (granted == 0) {
			return 0;
		}
		SharedFateMod.LOGGER.info("[PERK] 증강 {} 비행 부적 지급={}", perk.id(), granted);

		if (server != null && team != null) {
			refreshScreens(server, team);
		}
		return granted;
	}

	/** 공유 목록을 직접 고쳤으니 접속 중인 팀원의 화면을 맞춰 준다. */
	private static void refreshScreens(MinecraftServer server, ShareTeam team) {
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online == null || online.containerMenu == null) {
				continue;
			}
			online.containerMenu.broadcastChanges();
		}
	}

	// ------------------------------------------------------------------ 우클릭

	/** {@code UseItemCallback.EVENT} 에 붙는 지점. */
	public static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		try {
			return handle(player, level, hand);
		} catch (RuntimeException error) {
			warnOnce(error);
			return InteractionResult.PASS;
		}
	}

	private static InteractionResult handle(Player player, Level level, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || level == null || level.isClientSide()
				|| !(player instanceof ServerPlayer user)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!FlightCharmEffect.isFlightCharm(held)) {
			return InteractionResult.PASS;
		}

		TeamState state = TeamLookup.stateOf(user.getUUID());
		CharmEntry entry = findEntry(state);
		if (entry == null) {
			// 증강을 잃었으면 부적이 남아 있어도 아무 일도 하지 않는다.
			return InteractionResult.PASS;
		}
		FlightCharmEffect effect = entry.effect();
		// 가호는 「고른 사람 하나」가 강해지는 유형이다. 부적이 팀 공유 칸에 꽂혀 있어 누구나
		// 집을 수는 있지만, 쓰는 것은 고른 사람뿐이다 — 「가호 4」가 켜지면 전원이 쓴다.
		if (!PerkBlessingSet.appliesTo(state, entry.perkId(), user.getUUID())) {
			refuse(user, "고른 사람만 쓸 수 있습니다");
			return InteractionResult.FAIL;
		}
		if (user.getCooldowns().isOnCooldown(held)) {
			return InteractionResult.FAIL;
		}
		if (ACTIVE.containsKey(user.getUUID())) {
			refuse(user, "이미 날고 있습니다");
			return InteractionResult.FAIL;
		}
		// 원래 날 수 있는 사람의 것을 빼앗지 않는다. 켜 봐야 달라지는 것이 없으므로 쿨타임도
		// 걸지 않고 그대로 돌려보낸다.
		if (user.isCreative() || user.isSpectator()) {
			refuse(user, "지금은 이미 날 수 있습니다");
			return InteractionResult.FAIL;
		}

		// 「가호 3」이 켜져 있으면 더 오래 난다. 판정은 부적을 쓰는 이 순간의 팀 상태로 한다 —
		// 정의는 팀마다 공유되므로 미리 정해 두면 다른 팀의 시간까지 따라 바뀐다.
		//
		// 쿨타임은 여기서 걸지 않는다. 비행이 끝나는 순간부터 센다 — 아래 startCooldown 참고.
		start(user, effect, effect.flightTicksFor(state), held.copy());
		return InteractionResult.SUCCESS;
	}

	/**
	 * 비행을 켠다. 켜기 전 값을 기억해 두어야 나중에 되돌릴 수 있다.
	 *
	 * @param flightTicks 이번에 날 시간. 「가호 3」이면 정의의 강화값이 넘어온다
	 * @param cooldownKey 비행이 끝날 때 쿨타임을 걸 묶음의 사본
	 */
	private static void start(ServerPlayer user, FlightCharmEffect effect, int flightTicks,
			ItemStack cooldownKey) {
		boolean couldAlreadyFly = user.getAbilities().mayfly;
		if (!couldAlreadyFly) {
			user.getAbilities().mayfly = true;
			// 바꾼 값을 클라이언트에 알린다. 이것을 빠뜨리면 서버만 허가하고 화면은 그대로라
			// 두 번 뛰어도 날지 않는다. 바닐라 패킷이라 통신 규약이 올라가지 않는다.
			user.onUpdateAbilities();
		}
		ACTIVE.put(user.getUUID(),
				new Session(flightTicks, !couldAlreadyFly, effect.cooldownTicks(), cooldownKey));

		ServerLevel level = user.level();
		level.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.2F);
		TitleMessenger.showActionBar(user, Component
				.literal(PREFIX + "비행 부적 — " + seconds(flightTicks) + "초 동안 날 수 있습니다")
				.withStyle(ChatFormatting.AQUA));
	}

	/**
	 * 비행을 끈다.
	 *
	 * <p>우리가 켠 것이 아니면 아무것도 하지 않는다. 지금 크리에이티브·관전 모드인 사람도
	 * 건드리지 않는다 — 비행 도중에 모드가 바뀐 경우라, 여기서 끄면 그 모드의 비행을 빼앗는다.
	 */
	private static void stop(ServerPlayer user, Session session) {
		if (!session.grantedByUs || user.isCreative() || user.isSpectator()) {
			return;
		}
		user.getAbilities().mayfly = false;
		// 날고 있는 중이었다면 함께 내려야 한다. 이 칸을 끄지 않으면 클라이언트가 허가 없이
		// 계속 나는 모양이 되고 곧 이동 판정에 걸린다.
		user.getAbilities().flying = false;
		user.onUpdateAbilities();
	}

	/** 쓸 수 없는 상황을 알린다. 쿨타임은 걸지 않는다. */
	private static void refuse(ServerPlayer user, String reason) {
		user.sendSystemMessage(Component.literal(PREFIX + "비행 부적: " + reason)
				.withStyle(ChatFormatting.GRAY));
	}

	private static int seconds(int ticks) {
		return Math.max(1, ticks / TICKS_PER_SECOND);
	}

	// ------------------------------------------------------------------ 칸 잠금

	/**
	 * 이 칸을 잠가야 하는가. {@code SlotFlightCharmLockMixin} 이 물어보는 자리다.
	 *
	 * <p>싼 조건부터 본다. {@code mayPlace} 는 화면을 열 때마다 칸 수만큼 불리는 자리라,
	 * 대부분의 칸이 첫 두 줄에서 빠져나가야 한다.
	 *
	 * <p>잠그는 조건은 <b>「그 칸에 실제로 부적이 놓여 있고, 그 팀이 아직 증강을 가지고 있다」</b>
	 * 두 가지다. 증강을 잃으면 그 순간부터 평범한 칸으로 돌아가므로, 주기 점검이 부적을 지우기
	 * 전에도 사람이 갇히지 않는다.
	 */
	/**
	 * 지금 손에 든 것을 버리지 못하게 막아야 하는가. {@code PlayerDropLockMixin} 이 물어보는 자리다.
	 *
	 * <p>인벤토리를 열지 않고 버리는 길({@code Player.drop})은 {@link Slot} 을 지나지 않아
	 * {@link #isLockedSlot} 이 닿지 못한다. 판정 조건은 그쪽과 똑같다 — <b>고른 칸이 잠긴 칸이고,
	 * 거기 실제로 부적이 있고, 그 팀이 아직 증강을 가지고 있을 때</b>만 참이다.
	 *
	 * <p>다른 칸을 들고 있으면 언제나 거짓이라 <b>다른 아이템 버리기는 조금도 달라지지 않는다.</b>
	 */
	public static boolean blocksDrop(@Nullable ServerPlayer owner) {
		if (owner == null) {
			return false;
		}
		Inventory inventory = owner.getInventory();
		if (inventory.getSelectedSlot() != FlightCharmEffect.LOCKED_SLOT) {
			return false;
		}
		if (!FlightCharmEffect.isFlightCharm(inventory.getSelectedItem())) {
			return false;
		}
		return findEffect(TeamLookup.stateOf(owner.getUUID())) != null;
	}

	public static boolean isLockedSlot(@Nullable Slot slot) {
		if (slot == null || slot.getContainerSlot() != FlightCharmEffect.LOCKED_SLOT) {
			return false;
		}
		if (!(slot.container instanceof Inventory inventory)
				|| !(inventory.player instanceof ServerPlayer owner)) {
			return false;
		}
		if (!FlightCharmEffect.isFlightCharm(slot.getItem())) {
			return false;
		}
		return findEffect(TeamLookup.stateOf(owner.getUUID())) != null;
	}

	// ------------------------------------------------------------------ 주기

	/**
	 * 서버 틱마다 비행 시간을 재고, 주기마다 부적 자리를 점검한다.
	 *
	 * <p>{@code SharedFateMod} 의 서버 틱에 붙는다. 증강을 잃은 팀도 <b>반드시 이 자리를 지나야</b>
	 * 남아 있던 부적이 걷힌다. 그래서 보유 여부와 무관하게 모든 팀을 훑는다.
	 */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		try {
			tickFlights(server);
			if (++sweepCounter >= SWEEP_INTERVAL_TICKS) {
				sweepCounter = 0;
				sweepCharms(server);
			}
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void tickFlights(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		for (var iterator = ACTIVE.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<UUID, Session> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				// 접속이 끊겼다. onPlayerLeave 가 이미 되돌렸으므로 기록만 지운다.
				iterator.remove();
				continue;
			}
			Session session = entry.getValue();
			if (player.isRemoved() || player.isDeadOrDying()) {
				// 죽으면 그 자리에서 끝낸다. 부활은 새 플레이어 객체를 만들지만, 죽은 객체에
				// 참으로 남은 칸이 저장 자료로 새어 나가지 않도록 여기서 되돌려 둔다.
				stop(player, session);
				iterator.remove();
				startCooldown(player, session);
				continue;
			}
			if (findEffect(TeamLookup.stateOf(player.getUUID())) == null) {
				// 회차가 넘어가거나 「환골탈태」로 증강을 잃었다. 즉시 걷어낸다.
				stop(player, session);
				iterator.remove();
				startCooldown(player, session);
				expire(player, "증강을 잃어 비행이 끝났습니다");
				continue;
			}
			if (--session.remainingTicks <= 0) {
				stop(player, session);
				iterator.remove();
				startCooldown(player, session);
				expire(player, "비행이 끝났습니다");
				continue;
			}
			// 차원을 옮기면 클라이언트가 허가를 잊는다. 값이 되돌아가 있으면 도로 켜고 알린다.
			if (session.grantedByUs && !player.getAbilities().mayfly) {
				player.getAbilities().mayfly = true;
				player.onUpdateAbilities();
			}
			if (session.remainingTicks % ACTION_BAR_INTERVAL_TICKS == 0) {
				TitleMessenger.showActionBar(player, Component
						.literal(PREFIX + "비행 " + seconds(session.remainingTicks) + "초 남음")
						.withStyle(ChatFormatting.AQUA));
			}
		}
	}

	private static void expire(ServerPlayer player, String reason) {
		TitleMessenger.showActionBar(player,
				Component.literal(PREFIX + "비행 부적 — " + reason).withStyle(ChatFormatting.GRAY));
	}

	/**
	 * 비행이 끝난 <b>그 순간</b>부터 쿨타임을 센다.
	 *
	 * <h2>왜 쓸 때가 아니라 끝날 때인가</h2>
	 *
	 * <p>예전에는 우클릭하는 자리에서 걸었다. 그러면 비행 시간이 쿨타임 <b>안에서</b> 흘러,
	 * 10초 날고 쿨 60초인 정의가 실제로는 「50초만 기다리면 다시 난다」가 된다. 정의에 적은
	 * 숫자와 사람이 겪는 간격이 어긋나는 셈이라, 끝나는 자리로 옮겼다. 이제 한 판의 주기는
	 * <b>비행 + 쿨타임</b>이다.
	 *
	 * <p>부르는 곳은 비행이 끝나는 세 자리다 — 시간 만료 · 죽음 · 증강 상실. 셋 다 건다.
	 * 죽어서 끝난 것을 빼 주면 「죽으면 쿨타임이 없다」가 되어 죽는 것이 이득이 된다.
	 *
	 * <p><b>접속이 끊기면 걸지 않는다.</b> 바닐라의 {@code ItemCooldowns} 는 플레이어 저장
	 * 자료에 안 적혀서 다시 접속하면 어차피 비기 때문이다. 예전 방식에서도 마찬가지였으므로
	 * 이 판에서 새로 생긴 구멍은 아니다.
	 */
	private static void startCooldown(ServerPlayer player, Session session) {
		if (session.cooldownTicks <= 0 || session.cooldownKey.isEmpty()) {
			return;
		}
		player.getCooldowns().addCooldown(session.cooldownKey, session.cooldownTicks);
	}

	/**
	 * 팀마다 부적이 핫바 1번 칸에 하나만 있게 맞춘다.
	 *
	 * <p>세 가지를 한 번에 한다. 1번 칸 밖의 부적(주 인벤토리·확장 인벤토리·넘침 목록·왼손 등
	 * 장비 칸·엔더 상자)을 전부 지우고, 1번 칸의 개수를 하나로 깎고, 1번 칸에 부적이 없으면
	 * 새로 꽂는다. 증강이 없는 팀에서는 부적을 전부 지우기만 한다.
	 *
	 * <p>지우기가 <b>꽂기보다 먼저</b>라는 점이 중요하다. 그 순서라야 「없으면 다시 준다」가
	 * 개수를 불리지 않는다 — 칸을 지나지 않고 부적을 빼내는 길(Q 키·F 키)로 옮겨 간 부적은
	 * 새것을 꽂기 전에 이미 사라져 있다.
	 */
	private static void sweepCharms(MinecraftServer server) {
		TeamManager manager = TeamManager.get(server);
		for (ShareTeam team : List.copyOf(manager.allTeams())) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null) {
				continue;
			}
			try {
				if (sweepTeam(state, findEffect(state))) {
					refreshScreens(server, team);
				}
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("비행 부적 자리를 맞추지 못했습니다.", error);
			}
		}
	}

	/**
	 * 팀 하나의 부적 자리를 맞춘다.
	 *
	 * @param effect 이 팀이 가진 정의. 없으면 부적을 전부 걷어낸다
	 * @return 무언가 고쳤으면 참
	 */
	static boolean sweepTeam(TeamState state, @Nullable FlightCharmEffect effect) {
		boolean changed = false;

		// 1번 칸 밖에 있는 부적은 전부 지운다. 넘침 목록도 같다. 이 규칙이 있어야 「없으면
		// 다시 준다」가 개수를 불리지 않는다.
		for (int slot = 0; slot < state.mainItems.size(); slot++) {
			if (slot == FlightCharmEffect.LOCKED_SLOT) {
				continue;
			}
			if (FlightCharmEffect.isFlightCharm(state.mainItems.get(slot))) {
				state.mainItems.set(slot, ItemStack.EMPTY);
				changed = true;
			}
		}
		for (int slot = 0; slot < state.extraItems.size(); slot++) {
			if (FlightCharmEffect.isFlightCharm(state.extraItems.get(slot))) {
				state.extraItems.set(slot, ItemStack.EMPTY);
				changed = true;
			}
		}
		if (state.overflowItems.removeIf(FlightCharmEffect::isFlightCharm)) {
			changed = true;
		}
		if (clearEquipmentCharms(state)) {
			changed = true;
		}
		for (int slot = 0; slot < state.enderContainer.getContainerSize(); slot++) {
			if (FlightCharmEffect.isFlightCharm(state.enderContainer.getItem(slot))) {
				state.enderContainer.setItem(slot, ItemStack.EMPTY);
				changed = true;
			}
		}

		ItemStack locked = state.mainItems.get(FlightCharmEffect.LOCKED_SLOT);
		if (effect == null) {
			// 증강이 없는 팀이다. 1번 칸에 남아 있던 부적도 걷어낸다.
			if (FlightCharmEffect.isFlightCharm(locked)) {
				state.mainItems.set(FlightCharmEffect.LOCKED_SLOT, ItemStack.EMPTY);
				changed = true;
			}
			return changed;
		}

		if (FlightCharmEffect.isFlightCharm(locked)) {
			// 넘침 목록이 같은 성분끼리 합치는 길로 두 개가 겹칠 수 있다. 하나로 깎는다.
			if (locked.getCount() != 1) {
				locked.setCount(1);
				changed = true;
			}
			return changed;
		}

		// 죽어서 떨어뜨렸거나 회차가 넘어가 사라졌다. 제자리에 되돌려 놓는다.
		ItemStack fresh = effect.createItem();
		if (fresh == null || fresh.isEmpty()) {
			return changed;
		}
		placeInLockedSlot(state, fresh);
		return true;
	}

	/**
	 * 장비 칸에 들어간 부적을 걷어낸다.
	 *
	 * <p><b>F 키(손 바꾸기)를 막는 자리다.</b> 26.2 의 손 바꾸기는
	 * {@code ServerGamePacketListenerImpl.handlePlayerAction} 이 {@code setItemInHand} 두 번으로
	 * 처리해 칸({@code Slot})을 아예 지나지 않는다. 그래서 잠금 mixin 으로는 막히지 않고, 왼손에
	 * 건너간 부적을 여기서 지운 뒤 1번 칸에 새로 꽂는 것으로 되돌린다. 바꿔치기당한 원래
	 * 왼손 물건은 넘침 목록을 거쳐 빈 칸으로 돌아가므로 잃지 않는다.
	 *
	 * <p>{@code MAINHAND} 는 건드리지 않는다. 그 칸은 공유 장비 보관함이 아니라 인벤토리의
	 * 선택 칸을 그대로 가리키므로, 여기서 지우면 1번 칸의 부적 자신을 지우게 된다.
	 */
	private static boolean clearEquipmentCharms(TeamState state) {
		List<EquipmentSlot> found = new ArrayList<>();
		for (Map.Entry<EquipmentSlot, ItemStack> entry : state.equipment.view().entrySet()) {
			if (entry.getKey() != EquipmentSlot.MAINHAND
					&& FlightCharmEffect.isFlightCharm(entry.getValue())) {
				found.add(entry.getKey());
			}
		}
		for (EquipmentSlot slot : found) {
			state.equipment.set(slot, ItemStack.EMPTY);
		}
		return !found.isEmpty();
	}

	/**
	 * 부적을 핫바 1번 칸에 꽂는다. 원래 그 칸에 있던 것은 다른 빈 칸으로 보낸다.
	 *
	 * <p>먼저 꽂고 나중에 밀어야 한다. 반대로 하면 {@code restoreOverflow} 가 방금 비운 1번
	 * 칸을 도로 채워 부적이 들어갈 자리가 사라진다.
	 */
	private static void placeInLockedSlot(TeamState state, ItemStack charm) {
		ItemStack displaced = state.mainItems.get(FlightCharmEffect.LOCKED_SLOT);
		state.mainItems.set(FlightCharmEffect.LOCKED_SLOT, charm);
		if (displaced.isEmpty()) {
			return;
		}
		state.overflowItems.add(displaced);
		state.restoreOverflow(ExpandedInventoryManager.enabled());
		state.overflowItems.removeIf(ItemStack::isEmpty);
	}

	/** 이 팀의 핫바 1번 칸에 이미 부적이 있는가. */
	private static boolean holdsCharm(TeamState state) {
		return FlightCharmEffect.isFlightCharm(state.mainItems.get(FlightCharmEffect.LOCKED_SLOT));
	}

	// ------------------------------------------------------------------ 접속·부활

	/**
	 * 접속이 끊길 때 비행을 걷어낸다.
	 *
	 * <p>{@code Abilities.mayfly} 는 플레이어 저장 자료에 그대로 적힌다. 저장되기 전에 끄지
	 * 않으면 다음 접속에도 날 수 있는 <b>영구 비행</b>이 된다.
	 */
	public static void onPlayerLeave(@Nullable ServerPlayer player) {
		if (player == null) {
			return;
		}
		Session session = ACTIVE.remove(player.getUUID());
		if (session != null) {
			stop(player, session);
		}
	}

	/**
	 * 접속할 때, 흘린 비행 허가가 남아 있으면 지운다.
	 *
	 * <p>{@link #onPlayerLeave} 가 어떤 이유로든 지나가지 못한 경우(서버가 갑자기 죽는 등)를
	 * 위한 마지막 그물이다. 판단 근거는 <b>「바닐라는 생존·모험 모드에서 {@code mayfly} 를
	 * 참으로 만들지 않는다」</b> 하나다. 크리에이티브·관전 모드는 원래 참이므로 건드리지 않고,
	 * 이 모드의 팀에 속한 사람만 본다.
	 */
	public static void onPlayerJoin(@Nullable ServerPlayer player) {
		if (player == null || ACTIVE.containsKey(player.getUUID())) {
			return;
		}
		if (player.isCreative() || player.isSpectator() || !player.getAbilities().mayfly) {
			return;
		}
		if (TeamLookup.stateOf(player.getUUID()) == null) {
			return;
		}
		player.getAbilities().mayfly = false;
		player.getAbilities().flying = false;
		player.onUpdateAbilities();
		SharedFateMod.LOGGER.info("[PERK] 남아 있던 비행 부적의 비행 허가를 걷어냈습니다: {}",
				player.getPlainTextName());
	}

	/**
	 * 부활 직후에 부른다.
	 *
	 * <p>부활은 새 {@code ServerPlayer} 객체를 만들고, 그 객체의 {@code mayfly} 는 게임 모드가
	 * 정한 기본값이다. 그래서 여기서는 <b>기록만 지우면</b> 된다. 죽은 객체 쪽은
	 * {@link #tick} 의 사망 검사가 이미 되돌렸다.
	 */
	public static void onRespawn(@Nullable ServerPlayer oldPlayer, @Nullable ServerPlayer newPlayer) {
		if (oldPlayer != null) {
			ACTIVE.remove(oldPlayer.getUUID());
		}
		onPlayerJoin(newPlayer);
	}

	/**
	 * 서버가 멈출 때 부른다.
	 *
	 * <p>접속해 있는 사람의 {@code mayfly} 를 먼저 되돌리고 기록을 비운다. 이 자리를 빠뜨리면
	 * 서버를 끄는 순간 날고 있던 사람의 저장 자료에 비행 허가가 그대로 남는다.
	 */
	public static void reset() {
		ACTIVE.clear();
		sweepCounter = 0;
		warned = false;
	}

	/**
	 * 서버가 멈추기 <b>직전</b>에 부른다. {@link #reset} 은 서버가 완전히 멈춘 뒤라 저장이
	 * 이미 끝나 있어 너무 늦다.
	 */
	public static void onServerStopping(@Nullable MinecraftServer server) {
		if (server == null) {
			ACTIVE.clear();
			return;
		}
		for (UUID id : List.copyOf(ACTIVE.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			Session session = ACTIVE.remove(id);
			if (player != null && session != null) {
				stop(player, session);
			}
		}
	}

	// ------------------------------------------------------------------ 도우미

	/**
	 * 팀이 가진 비행 부적 증강과 그 효과.
	 *
	 * <p>{@code perkId} 를 함께 들고 다니는 이유는 <b>누가 쓸 수 있는지</b>를 가리기 위해서다.
	 * 「가호 4」 판정에는 어느 증강인지가 필요하다.
	 */
	record CharmEntry(String perkId, FlightCharmEffect effect) {
	}

	/** 팀이 가진 비행 부적 증강. 없으면 null. */
	static @Nullable CharmEntry findEntry(@Nullable TeamState state) {
		TeamState active = PerkGearRules.activeState(state);
		if (active == null) {
			return null;
		}
		for (String perkId : active.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof FlightCharmEffect charm) {
					return new CharmEntry(perkId, charm);
				}
			}
		}
		return null;
	}

	/** 팀이 가진 비행 부적 효과. 없으면 null. 「누가 쓸 수 있는가」는 보지 않는다. */
	static @Nullable FlightCharmEffect findEffect(@Nullable TeamState state) {
		CharmEntry entry = findEntry(state);
		return entry == null ? null : entry.effect();
	}

	/** 지금 날고 있는 사람의 uuid. 시험과 진단용이다. */
	public static List<UUID> flyingPlayers() {
		return new ArrayList<>(ACTIVE.keySet());
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn("비행 부적 처리에 실패했습니다. 이 경고는 한 번만 남습니다.", error);
	}
}
