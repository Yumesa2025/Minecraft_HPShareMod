package com.sharedfate.sync;

import com.mojang.authlib.GameProfile;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 프리즘 「완충」의 처치 면제를 <b>사망 진입점부터</b> 본다.
 *
 * <p>{@link SpreadDamageKillClearTest} 는 {@code clearPending}·{@code clearsOnKill} 같은 순수
 * 계산만 보고, 「누가 무엇을 죽였는가」를 가리는
 * {@link SpreadDamageManager#onDeath(LivingEntity, DamageSource)} 자체는 <b>살아 있는 서버가
 * 없어 지날 수 없다</b>고 적어 두었다. 여기서 그 말을 뒤집는다.
 *
 * <h2>어떻게 서버 없이 부르는가</h2>
 * <p>{@code onDeath} 가 실제로 만지는 것은 몇 개 되지 않는다 — {@code victim instanceof Mob},
 * {@code source.getEntity() instanceof ServerPlayer}, {@code killer.level().getServer()},
 * {@code TeamManager.get(server)}, {@code killer.getUUID()}. 그래서
 * {@code sun.misc.Unsafe.allocateInstance} 로 생성자를 지나지 않은 껍데기를 만들고 그 길목의
 * 칸만 채워 준다 — {@code EnchantmentDiamondCostTest} 가 {@code EnchantmentMenu} 에 쓰는 기법
 * 그대로다. 월드도 네트워크도 틱도 없지만, <b>이 코드가 보는 것은 전부 진짜</b>다.
 *
 * <p>팀 조회만은 껍데기로 때우지 않았다. {@link SavedDataStorage} 는 공개 생성자를 갖고 있고
 * 파일이 없으면 {@code readSavedData} 가 {@code null} 을 돌려주므로, 빈 폴더 하나로 진짜
 * 저장소를 세울 수 있다. 그래서 {@code TeamManager.get(server)} 는 <b>생산 코드가 부르는 그
 * 경로 그대로</b> 돈다.
 *
 * <h2>여기서도 보지 못하는 것</h2>
 * <p>바닐라가 {@code LivingEntity.die} 안에서 이 사건을 쏘아 준다는 것은 시험이 아니라
 * Fabric API jar 의 믹스인({@code LivingEntityMixin.notifyDeath},
 * {@code ServerPlayerMixin.notifyDeath})을 읽어 확인해야 한다. 이 시험이 보는 것은
 * <b>사건이 왔을 때 우리 코드가 하는 일</b>까지다.
 */
class SpreadDamageOnDeathTest {

	private static final float EPSILON = 0.0001F;

	private static final String 완충_풀 = """
			{
			  "perks": [
			    { "id": "sharedfate:cushion", "rarity": "prism", "name": "완충",
			      "effects": [ { "type": "spread_damage", "seconds": 8 } ] },
			    { "id": "sharedfate:그밖에", "rarity": "gold", "name": "그밖에",
			      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
			  ]
			}
			""";

	@TempDir
	Path 폴더;

	private MinecraftServer server;
	private ServerLevel level;
	private TeamManager manager;

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void 서버를_세운다() throws Exception {
		// 파일이 없으면 readSavedData 가 곧바로 null 을 돌려주므로 DataFixer 는 쓰이지 않는다.
		SavedDataStorage storage = new SavedDataStorage(폴더.resolve("data"), null,
				TestBootstrap.registries());
		server = (MinecraftServer) unsafe().allocateInstance(DedicatedServer.class);
		setField(MinecraftServer.class, server, "savedDataStorage", storage);
		level = (ServerLevel) unsafe().allocateInstance(ServerLevel.class);
		setField(ServerLevel.class, level, "server", server);
		// 생산 코드가 부르는 것과 같은 길로 꺼낸다. 같은 저장소를 보므로 같은 객체가 나온다.
		manager = TeamManager.get(server);
		assertSame(manager, TeamManager.get(server), "같은 서버에서는 같은 명단이 나와야 한다");
		loadPerks(완충_풀);
	}

	@AfterEach
	void 정리() {
		SpreadDamageManager.resetForTesting();
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ 사건이 닿는가

	/**
	 * {@code SpreadDamageManager::onDeath} 가 {@code AFTER_DEATH} 의 함수형 인터페이스에 실제로
	 * 꽂히고, 그 사건을 쏘면 우리 코드까지 닿는지 본다.
	 *
	 * <p>{@code SharedFateMod.onInitialize} 가 등록하는 그 줄({@code SharedFateMod} 218행)과 같은
	 * 모양이다. 모드 초기화 자체는 로더가 필요해 여기서 돌릴 수 없으므로, 같은 사건에 같은 방식
	 * 으로 붙여 <b>서명과 전달</b>만 확인한다.
	 */
	@Test
	void AFTER_DEATH_로_들어온_사망이_남은_몫을_지운다() throws Exception {
		ServerPlayer killer = 팀원("잡은사람");
		ShareTeam team = 팀(killer, "sharedfate:cushion");
		SpreadDamageManager.queueForTesting(team.teamId(), 12.0F, 4);

		ServerLivingEntityEvents.AFTER_DEATH.register(SpreadDamageManager::onDeath);
		ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(몹(), 때린사람(killer));

		assertEquals(0.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON,
				"사건을 타고 들어와도 남은 몫이 지워져야 한다");
	}

	// ------------------------------------------------------------------ 지워지는 경우

	@Test
	void 팀원이_몹을_잡으면_남은_몫이_지워진다() throws Exception {
		ServerPlayer killer = 팀원("잡은사람");
		ShareTeam team = 팀(killer, "sharedfate:cushion");
		SpreadDamageManager.queueForTesting(team.teamId(), 12.0F, 4);
		assertTrue(SpreadDamageManager.isSpreading(team.teamId()));

		SpreadDamageManager.onDeath(몹(), 때린사람(killer));

		assertEquals(0.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON);
		assertFalse(SpreadDamageManager.isSpreading(team.teamId()), "회복 금지도 함께 풀린다");
		assertTrue(SpreadDamageManager.trackedForTesting(team.teamId()),
				"표는 남는다 — 흉내 낸 무적시간을 잃으면 처치 직후 피해가 통째로 쌓인다");
	}

	/**
	 * 큐는 팀에 하나다. 맞은 사람과 잡은 사람이 달라도 같은 큐가 지워져야 한다.
	 */
	@Test
	void 맞은_사람이_아니어도_같은_팀이면_지워진다() throws Exception {
		ServerPlayer leader = 팀원("맞은사람");
		ShareTeam team = 팀(leader, "sharedfate:cushion");
		ServerPlayer mate = 팀원("잡은동료");
		assertTrue(manager.addMember(team.teamId(), mate.getUUID(), 4));
		SpreadDamageManager.queueForTesting(team.teamId(), 9.0F, 3);

		SpreadDamageManager.onDeath(몹(), 때린사람(mate));

		assertEquals(0.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON);
	}

	/** 이미 들어간 몫은 되돌리지 않는다. 사망 진입점을 지나도 그대로다. */
	@Test
	void 이미_들어간_몫은_사망_처리로도_되돌아오지_않는다() throws Exception {
		ServerPlayer killer = 팀원("잡은사람");
		ShareTeam team = 팀(killer, "sharedfate:cushion");
		SpreadDamageManager.queueForTesting(team.teamId(), 12.0F, 4);
		float delivered = SpreadDamageManager.takeSliceForTesting(team.teamId());

		SpreadDamageManager.onDeath(몹(), 때린사람(killer));

		assertEquals(3.0F, delivered, EPSILON);
		assertEquals(0.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON);
	}

	// ------------------------------------------------------------------ 지워지지 않는 경우

	@Test
	void 몹이_아닌_것을_부순_것은_세지_않는다() throws Exception {
		ServerPlayer killer = 팀원("잡은사람");
		ShareTeam team = 팀(killer, "sharedfate:cushion");
		SpreadDamageManager.queueForTesting(team.teamId(), 12.0F, 4);

		LivingEntity 갑옷거치대 = (LivingEntity) unsafe().allocateInstance(ArmorStand.class);
		SpreadDamageManager.onDeath(갑옷거치대, 때린사람(killer));

		assertEquals(12.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON,
				"갑옷 거치대는 Mob 이 아니다");
	}

	@Test
	void 사람이_잡지_않은_처치는_세지_않는다() throws Exception {
		ServerPlayer killer = 팀원("잡은사람");
		ShareTeam team = 팀(killer, "sharedfate:cushion");
		SpreadDamageManager.queueForTesting(team.teamId(), 12.0F, 4);

		// 좀비가 좀비를 죽였다.
		SpreadDamageManager.onDeath(몹(), new DamageSource(피해종류(DamageTypes.MOB_ATTACK), 몹()));
		assertEquals(12.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON);

		// 가해자가 아예 없는 피해(불·낙하 등).
		SpreadDamageManager.onDeath(몹(), new DamageSource(피해종류(DamageTypes.ON_FIRE)));
		assertEquals(12.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON);
	}

	@Test
	void 팀에_없는_사람의_처치는_세지_않는다() throws Exception {
		ServerPlayer killer = 팀원("잡은사람");
		ShareTeam team = 팀(killer, "sharedfate:cushion");
		SpreadDamageManager.queueForTesting(team.teamId(), 12.0F, 4);

		ServerPlayer 남 = 팀원("남");
		SpreadDamageManager.onDeath(몹(), 때린사람(남));

		assertEquals(12.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON,
				"다른 팀·무소속의 처치로 남의 큐가 지워지면 안 된다");
	}

	@Test
	void 완충이_없는_팀은_잡아도_지워지지_않는다() throws Exception {
		ServerPlayer killer = 팀원("잡은사람");
		ShareTeam team = 팀(killer, "sharedfate:그밖에");
		SpreadDamageManager.queueForTesting(team.teamId(), 12.0F, 4);

		SpreadDamageManager.onDeath(몹(), 때린사람(killer));

		assertEquals(12.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON);
	}

	@Test
	void 증강이_꺼진_판에서는_지워지지_않는다() throws Exception {
		ServerPlayer killer = 팀원("잡은사람");
		ShareTeam team = 팀(killer, "sharedfate:cushion");
		manager.stateByTeamId(team.teamId()).perksEnabled = false;
		SpreadDamageManager.queueForTesting(team.teamId(), 12.0F, 4);

		SpreadDamageManager.onDeath(몹(), 때린사람(killer));

		assertEquals(12.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON);
	}

	// ------------------------------------------------------------------ 팀원이 죽었을 때

	/**
	 * 팀원이 죽으면 <b>표째로</b> 버린다. 처치 면제와 다른 길이다 — 전멸한 팀의 흉내 낸
	 * 무적시간이 다음 회차로 넘어가면 안 된다.
	 */
	@Test
	void 팀원이_죽으면_표째로_버린다() throws Exception {
		ServerPlayer victim = 팀원("죽은사람");
		ShareTeam team = 팀(victim, "sharedfate:cushion");
		SpreadDamageManager.queueForTesting(team.teamId(), 12.0F, 4);

		SpreadDamageManager.onDeath(victim, new DamageSource(피해종류(DamageTypes.ON_FIRE)));

		assertEquals(0.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON);
		assertFalse(SpreadDamageManager.trackedForTesting(team.teamId()),
				"전멸은 표째로 버린다 — clearPending 이 아니라 forget 이다");
	}

	// ------------------------------------------------------------------ 터져도 죽지 않는가

	/**
	 * 몹이 죽는 모든 자리를 지나는 코드다. 여기서 예외가 새어 나가면 <b>사망 처리 전체</b>가
	 * 멈춘다. 월드가 없는 껍데기를 넘겨 그 자리를 실제로 터뜨려 본다.
	 */
	@Test
	void 사망_처리에서_터져도_예외를_내보내지_않는다() throws Exception {
		ServerPlayer killer = 팀원("잡은사람");
		ShareTeam team = 팀(killer, "sharedfate:cushion");
		SpreadDamageManager.queueForTesting(team.teamId(), 12.0F, 4);

		// 월드가 없는 사람 — killer.level().getServer() 에서 NPE 가 난다.
		ServerPlayer 떠돌이 = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
		setField(Entity.class, 떠돌이, "uuid", UUID.randomUUID());
		setField(Player.class, 떠돌이, "gameProfile",
				new GameProfile(UUID.randomUUID(), "떠돌이"));

		AtomicReference<Throwable> 터진것 = new AtomicReference<>();
		try {
			SpreadDamageManager.onDeath(몹(), 때린사람(떠돌이));
		} catch (Throwable error) {
			터진것.set(error);
		}

		assertEquals(null, 터진것.get(), "사망 처리로 예외가 새어 나가면 안 된다");
		assertEquals(12.0F, SpreadDamageManager.remaining(team.teamId()), EPSILON);
	}

	// ------------------------------------------------------------------ 도우미

	private ShareTeam 팀(ServerPlayer leader, String... ownedPerks) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		for (String perkId : ownedPerks) {
			state.ownedPerks.add(perkId);
		}
		ShareTeam team = manager.createTeam("팀" + leader.getUUID(), leader.getUUID(), state);
		assertTrue(team != null, "팀이 만들어져야 한다");
		return team;
	}

	/** 월드와 UUID 와 이름만 채운 {@code ServerPlayer} 껍데기. */
	private ServerPlayer 팀원(String name) throws Exception {
		ServerPlayer player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
		UUID id = UUID.randomUUID();
		setField(Entity.class, player, "level", level);
		setField(Entity.class, player, "uuid", id);
		// clearOnKill 이 남긴 몫을 지웠을 때 이름으로 기록을 남긴다. 비워 두면 그 줄에서 NPE 가
		// 나고, onDeath 가 그것을 삼켜 「지워졌는데 조용히 실패한」 모양이 된다.
		setField(Player.class, player, "gameProfile", new GameProfile(id, name));
		return player;
	}

	private static Mob 몹() throws Exception {
		return (Mob) unsafe().allocateInstance(Zombie.class);
	}

	private static DamageSource 때린사람(ServerPlayer killer) {
		return new DamageSource(피해종류(DamageTypes.PLAYER_ATTACK), killer);
	}

	private static Holder<DamageType> 피해종류(ResourceKey<DamageType> key) {
		return TestBootstrap.registries().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
	}

	private void loadPerks(String json) throws IOException {
		Path dir = 폴더.resolve("perks");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), json, StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
	}

	private static void setField(Class<?> owner, Object target, String name, Object value)
			throws Exception {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static sun.misc.Unsafe unsafe() throws Exception {
		Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return (sun.misc.Unsafe) field.get(null);
	}
}
