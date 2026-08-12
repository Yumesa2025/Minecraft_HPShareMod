package com.sharedfate.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class RunProgressState {
	public static final String PLAYING = "playing";
	public static final String VICTORY = "victory";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private int runNumber = 1;
	private String status = PLAYING;
	private String winningTeam = "";

	public static RunProgressState firstRun() {
		return new RunProgressState();
	}

	public static RunProgressState loadOrCreate(Path file) throws IOException {
		RunProgressState state = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				state = GSON.fromJson(reader, RunProgressState.class);
			} catch (RuntimeException ignored) {
			}
		}
		if (state == null) {
			state = firstRun();
		}
		state.sanitize();
		state.save(file);
		return state;
	}

	public int runNumber() {
		return runNumber;
	}

	public String status() {
		return status;
	}

	public String winningTeam() {
		return winningTeam;
	}

	public boolean isVictory() {
		return VICTORY.equals(status);
	}

	public void advanceToNextRun() {
		if (runNumber < Integer.MAX_VALUE) {
			runNumber++;
		}
		status = PLAYING;
		winningTeam = "";
	}

	public void markVictory(String teamName) {
		status = VICTORY;
		winningTeam = teamName == null || teamName.isBlank() ? "모험가" : teamName;
	}

	public void save(Path file) throws IOException {
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
			GSON.toJson(this, writer);
		}
		try {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void sanitize() {
		if (runNumber < 1) {
			runNumber = 1;
		}
		if (!PLAYING.equals(status) && !VICTORY.equals(status)) {
			status = PLAYING;
		}
		if (winningTeam == null) {
			winningTeam = "";
		}
		if (PLAYING.equals(status)) {
			winningTeam = "";
		} else if (winningTeam.isBlank()) {
			winningTeam = "모험가";
		}
	}
}
