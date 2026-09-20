package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 이제는 읽지 않는 옛 정의 파일을 알아보고 한 번 알려 준다.
 *
 * <p>0.26.6-dev 까지는 {@code config/} 에 증강·세트 정의를 꺼내 놓고 그다음부터 그 파일만
 * 읽었다. 판을 올려도 덮어쓰지 않아 <b>판마다 사람이 손으로 지워야 했고</b>, 하나만 지우면
 * 반쪽이 옛 정의로 읽히는데 오류도 나지 않았다. 0.26.7-dev 부터 정의는 모드 안에만 있다.
 *
 * <p>그런데 이미 돌던 서버에는 그 파일이 남아 있다. 조용히 무시하면, 값을 직접 고쳐 쓰던
 * 사람이 <b>영문도 모른 채 자기 값이 안 먹는 것</b>을 겪는다. 그래서 읽지는 않되 남아 있으면
 * 한 번 알려 준다. <b>지우지는 않는다</b> — 읽지 않으니 해가 없고, 남의 파일을 마음대로
 * 지울 이유가 없다.
 *
 * <p><b>두 파일을 여기 한 자리에 모아 둔 것이 요점이다.</b> 이 코드베이스가 되풀이해서 당한
 * 사고가 정확히 「한쪽만 챙기고 다른 쪽을 잊는 것」이었다. 목록에 둘 다 있으니 한쪽만
 * 빠뜨리는 일이 구조적으로 불가능하다.
 */
public final class LegacyDefinitionFiles {

	/** 더 이상 읽지 않는 파일들. 증강과 세트가 <b>반드시 함께</b> 들어 있어야 한다. */
	private static final List<String> NAMES =
			List.of(PerkRegistry.FILE_NAME, PerkSetRegistry.FILE_NAME);

	private LegacyDefinitionFiles() {
	}

	/**
	 * 설정 폴더에 남아 있는 옛 정의 파일의 이름들. 순수 조회라 시험이 닿는다.
	 *
	 * @param configDir 설정 폴더. {@code null} 이면 빈 목록
	 * @return 실제로 있는 파일 이름들. {@link #NAMES} 의 순서를 지킨다
	 */
	public static List<String> presentIn(@Nullable Path configDir) {
		if (configDir == null) {
			return List.of();
		}
		List<String> found = new ArrayList<>(NAMES.size());
		for (String name : NAMES) {
			try {
				if (Files.isRegularFile(configDir.resolve(name))) {
					found.add(name);
				}
			} catch (Exception error) {
				// 설정 폴더를 못 들여다보는 상황이라도 서버가 뜨는 것을 막을 이유가 없다.
				SharedFateMod.LOGGER.debug("옛 정의 파일을 확인하지 못했습니다: {}", name, error);
			}
		}
		return List.copyOf(found);
	}

	/** 남아 있는 옛 정의 파일이 있으면 서버가 뜰 때 한 번 알려 준다. */
	public static void noticeIfPresent(@Nullable Path configDir) {
		List<String> found = presentIn(configDir);
		if (found.isEmpty()) {
			return;
		}
		SharedFateMod.LOGGER.warn(
				"{} 은(는) 이제 읽지 않습니다. 증강·세트 정의는 모드 안에 들어 있어, 판을 올릴 때"
						+ " 정의 파일을 지울 필요가 없어졌습니다. 남은 파일은 지우셔도 됩니다"
						+ " (지워도 아무것도 달라지지 않습니다). 값을 직접 고쳐 쓰고 계셨다면"
						+ " 그 내용은 이제 반영되지 않습니다: {}",
				String.join(", ", found), configDir);
	}
}
