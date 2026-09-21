package com.sharedfate.sync;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 초기화가 <b>지우기 전에 검사하는가</b>. 코드의 줄 순서를 시험으로 못박는다.
 *
 * <p>{@code RunResetCoordinator.reset} 과 {@code WorldResetCoordinator.tick} 은
 * {@code MinecraftServer} 를 끼워 넣을 수 없어 단위 시험이 닿지 않는다. 그래서 이 파일이
 * 나오기 전까지 「거절할 때는 아무것도 안 지운다」는 <b>코드에 적힌 순서로만</b> 보장됐다.
 * 검사를 한 줄 아래로 옮기거나 되돌림 호출을 지워도 빌드도 시험도 조용하다.
 *
 * <p>그 사고는 이미 한 번 났다 — 0.28.0-dev 에서 {@code /shareteam reset} 이 팀을 해체한
 * <b>5초 뒤에</b> 월드 표식을 썼고, 그 사이 실패하면 <b>팀만 사라진 채 서버가 계속 돌았다.</b>
 * 되돌릴 수 없는 명령에서 가장 나쁜 결말이다.
 *
 * <h2>왜 메서드를 불러 보지 않고 바이트코드를 읽는가</h2>
 * <p>부작용을 인터페이스로 뽑아 가짜를 끼우면 제대로 시험할 수 있지만, 잘 돌고 있는 코드를
 * 시험 때문에 다시 짜는 일이 된다. 여기서 지키려는 것은 <b>호출의 순서</b> 하나뿐이고, 그것은
 * 클래스 파일에 그대로 적혀 있다. 싸게 못박고 설계는 그대로 둔다.
 *
 * <p><b>한계를 알고 쓴다.</b> 이 시험은 「호출이 이 순서로 적혀 있다」까지만 본다. 조건이
 * 실제로 무엇을 보는지, 되돌림이 옳게 동작하는지는 못 본다. 그것까지 보려면 위의 리팩터링이
 * 필요하다.
 */
class RunResetOrderTest {

	/** 이 중 하나라도 부르고 나면 되돌릴 수 없다. */
	private static final List<String> DESTRUCTIVE = List.of(
			"RunResetMarker.write",
			"RunResetCoordinator.disbandAllTeams",
			"DamageLedger.clearAllRecords",
			"WorldResetCoordinator.requestRunReset");

	// ------------------------------------------------------------------ reset 의 차례

	/**
	 * <b>검사가 맨 앞이다.</b> 무엇을 건드리기 전에 온다.
	 *
	 * <p>{@code blocker} 는 이미 카운트다운이 도는지, 월드가 지울 수 있는 자리인지, 지난 초기화
	 * 표식이 남아 있는지를 본다. 이것이 지우는 호출 뒤로 밀리면 <b>거절당한 사람도 팀을 잃는다.</b>
	 */
	@Test
	void 초기화는_아무것도_지우기_전에_먼저_검사한다() throws IOException {
		List<String> events = traceOf(RunResetCoordinator.class, "reset");

		int guard = events.indexOf("call:RunResetCoordinator.blocker");
		assertTrue(guard >= 0, "reset 이 blocker 를 아예 부르지 않는다");

		int firstCall = indexOfFirst(events, "call:");
		assertEquals(guard, firstCall,
				"blocker 앞에 다른 호출이 있다. 검사보다 먼저 하는 일이 있으면 안 된다: "
						+ events.get(firstCall));

		int firstDamage = firstDestructive(events);
		assertTrue(firstDamage > guard,
				"지우는 호출이 검사보다 앞에 있다: " + events.get(firstDamage));

		assertTrue(hasReturnBetween(events, guard, firstDamage),
				"검사와 지우기 사이에 빠져나가는 길이 없다. 검사가 막아도 그대로 지운다");
	}

	/**
	 * <b>회차 표식을 먼저 쓰고, 못 쓰면 거기서 그만둔다.</b>
	 *
	 * <p>순서가 뒤집히면 「팀은 사라졌는데 회차는 1로 안 눌리는」 상태가 남는다. 월드는 새로
	 * 열리는데 회차 번호만 이어지는 것이라, 기록이 조용히 어긋난다.
	 */
	@Test
	void 회차_표식을_쓰지_못하면_팀을_해체하지_않는다() throws IOException {
		List<String> events = traceOf(RunResetCoordinator.class, "reset");

		int marker = events.indexOf("call:RunResetMarker.write");
		int disband = events.indexOf("call:RunResetCoordinator.disbandAllTeams");
		assertTrue(marker >= 0, "회차 표식을 쓰지 않는다");
		assertTrue(disband >= 0, "팀을 해체하지 않는다");
		assertTrue(marker < disband, "표식을 쓰기 전에 팀을 해체한다");

		assertTrue(hasReturnBetween(events, marker, disband),
				"표식 쓰기가 실패해도 그대로 해체로 넘어간다. 그 사이에 빠져나가는 길이 있어야 한다");
	}

	/** 카운트다운은 <b>맨 마지막</b>이다. 지울 것을 다 지운 뒤에 건다. */
	@Test
	void 카운트다운은_지운_다음에_건다() throws IOException {
		List<String> events = traceOf(RunResetCoordinator.class, "reset");

		int countdown = events.indexOf("call:WorldResetCoordinator.requestRunReset");
		assertTrue(countdown >= 0, "카운트다운을 걸지 않는다");
		for (String call : List.of("call:RunResetCoordinator.disbandAllTeams",
				"call:DamageLedger.clearAllRecords")) {
			assertTrue(events.indexOf(call) < countdown,
					call + " 이 카운트다운보다 뒤에 있다. 서버가 내려가는 중에 지우게 된다");
		}
	}

	// ------------------------------------------------------------------ tick 의 되돌림

	/**
	 * 5초 뒤에 실패하면 <b>회차 표식을 거둔다.</b>
	 *
	 * <p>이 되돌림이 사라지면 「초기화는 안 됐는데 회차만 1로 눌리는」 상태가 남는다. 다음에
	 * 서버를 켜는 순간 멀쩡한 진행이 조용히 1회차로 돌아간다 — <b>아무 경고도 없이.</b>
	 *
	 * <p>호출이 예외 처리 구간 안에 있는지까지 본다. 성공 경로로 옮겨 놓으면 정상 초기화가
	 * 자기 표식을 지워 버려서, 회차가 영영 1로 안 눌린다.
	 */
	@Test
	void 월드_표식_쓰기가_실패하면_회차_표식을_거둔다() throws IOException {
		Trace trace = fullTraceOf(WorldResetCoordinator.class, "tick");

		int rollback = trace.events().indexOf("call:WorldResetCoordinator.rollbackRunResetMarker");
		assertTrue(rollback >= 0,
				"실패 경로가 회차 표식을 거두지 않는다. 멀쩡한 회차가 다음 기동에 1로 눌린다");

		assertTrue(trace.handlerIndices().stream().anyMatch(handler -> handler < rollback),
				"회차 표식 거두기가 예외 처리 구간 밖에 있다. 성공한 초기화까지 자기 표식을 지운다");
	}

	// ------------------------------------------------------------------ 읽는 도구

	private static int indexOfFirst(List<String> events, String prefix) {
		for (int i = 0; i < events.size(); i++) {
			if (events.get(i).startsWith(prefix)) {
				return i;
			}
		}
		return -1;
	}

	/** 지우는 호출 중 가장 먼저 오는 것의 자리. 하나도 없으면 시험을 세운다. */
	private static int firstDestructive(List<String> events) {
		int first = Integer.MAX_VALUE;
		for (String call : DESTRUCTIVE) {
			int at = events.indexOf("call:" + call);
			if (at >= 0) {
				first = Math.min(first, at);
			}
		}
		assertTrue(first != Integer.MAX_VALUE,
				"지우는 호출이 하나도 없다. 이 시험이 지키려던 메서드가 아니다");
		return first;
	}

	/** 두 자리 사이에 {@code return} 이 있는가 — 곧 빠져나가는 길이 있는가. */
	private static boolean hasReturnBetween(List<String> events, int from, int to) {
		for (int i = from + 1; i < to; i++) {
			if (events.get(i).equals("return")) {
				return true;
			}
		}
		return false;
	}

	private static List<String> traceOf(Class<?> owner, String methodName) throws IOException {
		return fullTraceOf(owner, methodName).events();
	}

	/**
	 * 메서드 하나를 읽어 <b>호출·반환·표식</b>이 나오는 차례를 적는다.
	 *
	 * <p>asm-tree 를 쓰지 않고 방문자만 쓴다 — 코어 ASM 은 믹스인이 이미 끌고 오므로 의존성이
	 * 늘지 않는다.
	 */
	private static Trace fullTraceOf(Class<?> owner, String methodName) throws IOException {
		List<String> events = new ArrayList<>();
		Set<Label> handlers = new HashSet<>();
		List<Integer> handlerIndices = new ArrayList<>();

		ClassReader reader = new ClassReader(classBytes(owner));
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor,
					String signature, String[] exceptions) {
				if (!name.equals(methodName)) {
					return null;
				}
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitTryCatchBlock(Label start, Label end, Label handler,
							String type) {
						handlers.add(handler);
					}

					@Override
					public void visitLabel(Label label) {
						if (handlers.contains(label)) {
							handlerIndices.add(events.size());
						}
					}

					@Override
					public void visitMethodInsn(int opcode, String callOwner, String callName,
							String descriptor, boolean isInterface) {
						events.add("call:" + simpleName(callOwner) + "." + callName);
					}

					@Override
					public void visitInsn(int opcode) {
						if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
							events.add("return");
						}
					}
				};
			}
		}, ClassReader.SKIP_FRAMES);

		assertTrue(!events.isEmpty(),
				owner.getSimpleName() + "." + methodName + " 을 읽지 못했다");
		return new Trace(events, handlerIndices);
	}

	private static String simpleName(String internalName) {
		return internalName.substring(internalName.lastIndexOf('/') + 1);
	}

	private static byte[] classBytes(Class<?> type) throws IOException {
		String path = "/" + type.getName().replace('.', '/') + ".class";
		try (InputStream in = type.getResourceAsStream(path)) {
			if (in == null) {
				throw new IOException("클래스 파일을 찾지 못했습니다: " + path);
			}
			return in.readAllBytes();
		}
	}

	/**
	 * 읽어 낸 차례.
	 *
	 * @param events         호출·반환이 나온 차례
	 * @param handlerIndices 예외 처리 구간이 시작되는 자리들
	 */
	private record Trace(List<String> events, List<Integer> handlerIndices) {
	}
}
