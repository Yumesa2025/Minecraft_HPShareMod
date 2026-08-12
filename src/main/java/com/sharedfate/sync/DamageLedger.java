package com.sharedfate.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

public final class DamageLedger {
	public static final String FILE_NAME = "sharedfate-damage-history.json";
	private static final int FORMAT_VERSION = 1;
	private static final int MAX_TEAMS = 1024;
	private static final int MAX_PLAYERS_PER_TEAM = 16;
	private static final int MAX_RUNS_PER_PLAYER = 100_000;
	private static final int RUNS_PER_PAGE = 12;
	private static final int MAX_BOOK_PAGES = 100;
	private static final long AUTO_FLUSH_INTERVAL_NANOS = 1_000_000_000L;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static LedgerFile data = new LedgerFile();
	private static Path file;
	private static boolean dirty;
	private static long nextAutomaticFlush;

	private static final class LedgerFile {
		int formatVersion = FORMAT_VERSION;
		Map<String, TeamEntry> teams = new LinkedHashMap<>();
	}

	private static final class TeamEntry {
		String name = "";
		Map<String, PlayerEntry> players = new LinkedHashMap<>();
	}

	private static final class PlayerEntry {
		String name = "";
		Map<String, Double> runs = new LinkedHashMap<>();
	}

	private DamageLedger() {
	}

	public static void onServerStarted(MinecraftServer server) {
		file = server.getServerDirectory().toAbsolutePath().normalize().resolve(FILE_NAME);
		data = load(file);
		dirty = false;
		nextAutomaticFlush = 0L;
	}

	public static void record(ShareTeam team, ServerPlayer player, float damage) {
		if (file == null || team == null || player == null || !Float.isFinite(damage) || damage <= 0.0F) {
			return;
		}
		record(team.teamId(), team.name(), player.getUUID(), player.getPlainTextName(),
				RunProgressManager.runNumber(), damage);
	}

	static void record(UUID teamId, String teamName, UUID playerId, String playerName,
			int runNumber, double damage) {
		if (teamId == null || playerId == null || runNumber < 1
				|| !Double.isFinite(damage) || damage <= 0.0D) {
			return;
		}
		TeamEntry team = data.teams.computeIfAbsent(teamId.toString(), ignored -> new TeamEntry());
		team.name = safeName(teamName, "이름 없는 팀");
		PlayerEntry player = team.players.computeIfAbsent(playerId.toString(), ignored -> new PlayerEntry());
		player.name = safeName(playerName, playerId.toString().substring(0, 8));
		player.runs.merge(Integer.toString(runNumber), damage, Double::sum);
		dirty = true;
	}

	public static void flushIfDirty() {
		if (!dirty || file == null) {
			return;
		}
		try {
			save(file, data);
			dirty = false;
		} catch (IOException e) {
			SharedFateMod.LOGGER.error("피해 기록을 저장하지 못했습니다: {}", file, e);
		}
	}

	public static void flushIfDue() {
		long now = System.nanoTime();
		if (!dirty || now < nextAutomaticFlush) {
			return;
		}
		flushIfDirty();
		nextAutomaticFlush = now + AUTO_FLUSH_INTERVAL_NANOS;
	}

	public static void giveVictoryBooks(MinecraftServer server, ShareTeam team, int currentRun) {
		if (team == null) {
			return;
		}
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player != null) {
				ensureMember(team, player);
			}
		}
		ItemStack template = createBook(team, currentRun);
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player == null) {
				continue;
			}
			ItemStack book = template.copy();
			if (!player.addItem(book)) {
				player.drop(book, false);
			}
		}
		flushIfDirty();
	}

	static List<String> buildPageTexts(ShareTeam team, int currentRun) {
		TeamEntry storedTeam = data.teams.get(team.teamId().toString());
		List<String> pages = new ArrayList<>();
		pages.add("SharedFate 피해 기록\n\n팀: " + team.name()
				+ "\n완주 회차: " + Math.max(1, currentRun)
				+ "\n\n표시 단위: 피해 / 하트");
		for (UUID member : team.members()) {
			PlayerEntry entry = storedTeam == null ? null : storedTeam.players.get(member.toString());
			String playerName = entry == null
					? member.toString().substring(0, 8) : safeName(entry.name, member.toString().substring(0, 8));
			int safeCurrentRun = Math.min(MAX_RUNS_PER_PLAYER, Math.max(1, currentRun));
			List<Integer> runs = IntStream.rangeClosed(1, safeCurrentRun).boxed().toList();
			double total = entry == null ? 0.0D : runs.stream()
					.mapToDouble(run -> finiteDamage(entry.runs.get(Integer.toString(run))))
					.sum();
			for (int from = 0; from < runs.size() && pages.size() < MAX_BOOK_PAGES; from += RUNS_PER_PAGE) {
				int to = Math.min(runs.size(), from + RUNS_PER_PAGE);
				StringBuilder page = new StringBuilder(playerName);
				if (from > 0) {
					page.append(" (계속)");
				}
				page.append("\n\n");
				for (int index = from; index < to; index++) {
					int run = runs.get(index);
					double amount = entry == null ? 0.0D
							: finiteDamage(entry.runs.get(Integer.toString(run)));
					page.append(run).append("회차: ").append(formatDamage(amount)).append('\n');
				}
				if (to == runs.size()) {
					page.append("\n총합: ").append(formatDamage(total));
				}
				pages.add(page.toString());
			}
		}
		if (pages.size() >= MAX_BOOK_PAGES) {
			pages.set(MAX_BOOK_PAGES - 1, "기록이 너무 많아 책의 100쪽 한도에서 잘렸습니다.\n"
					+ "원본: " + FILE_NAME);
		}
		return List.copyOf(pages);
	}

	private static ItemStack createBook(ShareTeam team, int currentRun) {
		List<Filterable<Component>> pages = buildPageTexts(team, currentRun).stream()
				.<Filterable<Component>>map(text -> Filterable.passThrough(Component.literal(text)))
				.toList();
		WrittenBookContent content = new WrittenBookContent(
				Filterable.passThrough("SharedFate 피해 기록"), "SharedFate", 0, pages, true);
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
		return book;
	}

	private static void ensureMember(ShareTeam sourceTeam, ServerPlayer sourcePlayer) {
		TeamEntry team = data.teams.computeIfAbsent(sourceTeam.teamId().toString(), ignored -> new TeamEntry());
		team.name = safeName(sourceTeam.name(), "이름 없는 팀");
		PlayerEntry player = team.players.computeIfAbsent(
				sourcePlayer.getUUID().toString(), ignored -> new PlayerEntry());
		player.name = safeName(sourcePlayer.getPlainTextName(), sourcePlayer.getUUID().toString().substring(0, 8));
		dirty = true;
	}

	private static LedgerFile load(Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			return new LedgerFile();
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			LedgerFile loaded = GSON.fromJson(reader, LedgerFile.class);
			return sanitize(loaded);
		} catch (IOException | RuntimeException e) {
			SharedFateMod.LOGGER.error("피해 기록 파일이 손상되어 새 기록을 메모리에서 시작합니다: {}", path, e);
			return new LedgerFile();
		}
	}

	private static LedgerFile sanitize(LedgerFile loaded) {
		if (loaded == null || loaded.formatVersion != FORMAT_VERSION || loaded.teams == null
				|| loaded.teams.size() > MAX_TEAMS) {
			return new LedgerFile();
		}
		loaded.teams.entrySet().removeIf(entry -> {
			try {
				UUID.fromString(entry.getKey());
			} catch (RuntimeException e) {
				return true;
			}
			TeamEntry team = entry.getValue();
			return team == null || team.players == null || team.players.size() > MAX_PLAYERS_PER_TEAM;
		});
		for (TeamEntry team : loaded.teams.values()) {
			team.name = safeName(team.name, "이름 없는 팀");
			team.players.entrySet().removeIf(entry -> {
				try {
					UUID.fromString(entry.getKey());
				} catch (RuntimeException e) {
					return true;
				}
				PlayerEntry player = entry.getValue();
				return player == null || player.runs == null || player.runs.size() > MAX_RUNS_PER_PLAYER;
			});
			for (PlayerEntry player : team.players.values()) {
				player.name = safeName(player.name, "알 수 없음");
				player.runs.entrySet().removeIf(entry -> parsePositiveInt(entry.getKey()) < 1
						|| finiteDamage(entry.getValue()) <= 0.0D);
			}
		}
		return loaded;
	}

	private static void save(Path path, LedgerFile ledger) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
			GSON.toJson(ledger, writer);
		}
		try {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static int parsePositiveInt(String value) {
		try {
			return Integer.parseInt(value);
		} catch (RuntimeException ignored) {
			return -1;
		}
	}

	private static double finiteDamage(Double value) {
		return value != null && Double.isFinite(value) && value > 0.0D ? value : 0.0D;
	}

	private static String safeName(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.substring(0, Math.min(64, value.length()));
	}

	private static String formatDamage(double amount) {
		return String.format(Locale.ROOT, "%.1f / %.1f♥", amount, amount / 2.0D);
	}

	static void clearState() {
		data = new LedgerFile();
		file = null;
		dirty = false;
		nextAutomaticFlush = 0L;
	}

	public static void resetRuntime() {
		flushIfDirty();
		clearState();
	}
}
