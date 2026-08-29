# 팀 증강(Perk) 시스템 설계

- 상태: 승인됨
- 대상 버전: 0.5.0-dev
- 작성일: 2026-08-28

## 요약

팀 공유 레벨이 5의 배수에 처음 도달할 때마다, 팀원 중 무작위로 뽑힌 한 명이
증강 3개 중 하나를 고른다. 고른 효과는 팀 전체에 적용되고 그 회차 내내 유지된다.
전멸로 회차가 넘어가면 전부 초기화된다.

증강 목록 자체는 이 설계의 범위 밖이다. 이 문서는 증강을 담을 **틀**만 정의한다.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 발동 조건 | 팀 공유 레벨(`TeamState.xpLevel`)이 5·10·15·20·25·30·35에 **처음** 도달 |
| 구간 수 | 7회 (35렙 이후로는 없음) |
| 선택자 | 접속 중인 팀원 중 무작위 1명 |
| 효과 범위 | **팀 전체** |
| 효과 성격 | 상시 패시브(로그라이트형) + 장단점 교환(트레이드오프형) 혼합 |
| 창 열기 | 자동으로 열지 않음. 알림 후 본인이 `/shareteam perk` 로 직접 연다 |
| 관전 | 다른 팀원이 열면 같은 화면을 읽기 전용으로 본다 |
| 켜고끄기 | 팀 생성 시 리더가 결정, 그 뒤 변경 불가 |
| 회차 이월 | 전부 초기화 (`perksEnabled` 만 유지) |
| 후보 구성 | 구간마다 등급이 하나로 정해지고 그 등급에서만 3개. 15렙은 플레 고정 |
| 중복 | **중첩 없음.** 한 번 고른 증강은 그 회차 동안 후보에서 영구히 제외 |
| 정의 위치 | `config/sharedfate-perks.json` + Java 핸들러 하이브리드 |

## 발동 조건 상세

`TeamState.lastPerkMilestone` 에 마지막으로 처리한 구간을 기록한다.
현재 `xpLevel` 이 `lastPerkMilestone` 보다 큰 5의 배수에 도달하면 그 구간을 발동시키고
`lastPerkMilestone` 을 갱신한다.

경험치를 써서 레벨이 내려갔다가 다시 올라와도 재발동하지 않는다.
레벨이 한 번에 여러 구간을 건너뛰면(예: 2렙 → 9렙) 건너뛴 구간마다 각각 발동해
대기열에 순서대로 쌓인다.

## 아키텍처

새 기능은 `perk/` 패키지로 격리한다. 기존 파일 수정은 최소화한다.

```
src/main/java/com/sharedfate/perk/
  Perk.java              증강 정의 (id, 이름, 설명, 등급, 효과 목록)
  PerkRarity.java        enum SILVER / GOLD / PLATINUM (실버 / 골드 / 플레)
  PerkEffect.java        효과 인터페이스 (apply / remove)
  PerkEffectType.java    JSON type 문자열 → 효과 팩토리 매핑
  effect/AttributeEffect.java
  effect/DamageDealtEffect.java
  effect/DamageTakenEffect.java
  effect/StatusEffectPerk.java
  effect/CustomEffect.java
  PerkRegistry.java      JSON 로드 + Java 커스텀 핸들러 등록을 합쳐 id→Perk 조회
  PerkDraft.java         구간 → 등급 배정. 보유한 것을 뺀 같은 등급 후보 3개 추첨
  PerkManager.java       구간 감지 → 추첨 → 대기열 → 적용 (서버 틱)
  (보유 증강은 TeamState 의 List<String>. 중첩이 없어 별도 record 가 필요 없다)
  PendingOffer.java      record {int milestone, UUID chooser, List<String> optionIds}

src/main/java/com/sharedfate/command/PerkCommand.java
src/main/java/com/sharedfate/net/PerkOfferPayload.java      S2C 후보 제시
src/main/java/com/sharedfate/net/PerkChoiceC2SPayload.java  C2S 선택 전송
src/main/java/com/sharedfate/net/PerkSyncPayload.java       S2C 보유 증강 동기화

src/client/java/com/sharedfate/client/perk/PerkOfferScreen.java
src/client/java/com/sharedfate/client/perk/PerkClientState.java
```

### 기존 파일 수정

| 파일 | 수정 내용 |
|---|---|
| `SharedFateMod.java` | `PerkRegistry.load()`, `PerkManager` 틱·접속·퇴장·리스폰·종료 배선 |
| `TeamState.java` | 필드 4개 추가 + Codec `optionalFieldOf` |
| `SharedFateNetworking.java` | 페이로드 3개 등록, `PROTOCOL_VERSION` 5 → 7 |
| `ShareTeamCommand.java` | `create` 에 증강 켜고끄기 인자 추가 |

`ShareTeamCommand.java` 는 이미 499줄로 저장소에서 가장 큰 파일이다.
증강 하위명령은 `PerkCommand` 로 분리한다. `PerkCommand` 는 `node()` 만 노출하고
`ShareTeamCommand` 가 `.then(PerkCommand.node())` 로 붙인다. `SharedFateMod` 에서
따로 등록하지 않는다 — 등록 경로가 둘이 되면 중복 등록이 된다.

### 알려진 제약

Brigadier 는 첫 단어가 리터럴과 일치하면 그 리터럴 노드로만 파싱하고 argument 노드로
되돌아가지 않는다. `create` 의 팀 이름이 greedy 인자이므로 옵션을 앞에 두는
`create perks <on|off> <이름>` 구조를 쓸 수밖에 없고, 그 결과
**`perks` 로 시작하는 팀 이름은 만들 수 없다.** 기존 `create <이름>` 사용법은 그대로 동작한다.

## 데이터 모델

`TeamState` 에 필드 4개를 추가한다. 기존 Codec 패턴대로 `optionalFieldOf` 를 쓰므로
증강 필드가 없는 기존 월드도 그대로 열린다.

```java
public boolean perksEnabled;              // 팀 생성 시 결정, 회차 내내 고정
public int lastPerkMilestone;             // 마지막 처리 구간 (0, 5, 10, …, 35)
public final List<String> ownedPerks;     // 보유 증강 id. 같은 id 는 한 번만
public final List<PendingOffer> pending;  // 대기 중인 선택권, 여러 개 가능
```

### 보유 증강 저장 형식과 하위호환

`ownedPerks` 는 증강 id 문자열 목록으로 저장한다.

중첩 개념이 있던 시절에는 `{ "perkId": …, "count": … }` 객체 목록이었다. 이미 돌아가는
서버의 월드에 그 형태가 들어 있으므로 **읽을 때는 두 형태를 모두 받아들인다**
(`Codec.either(Codec.STRING, Codec.STRING.fieldOf("perkId").codec())`).
`count` 는 뜻이 사라졌으므로 읽고 버린다. 새로 저장할 때는 문자열만 적는다.
같은 id 가 두 번 들어 있으면 `TeamState.sanitizePerks` 가 한 개로 접는다.

### 후보 확정 저장

`PendingOffer.optionIds` 는 **추첨 시점에 확정해 저장**한다.
창을 열 때마다 뽑으면, 마음에 안 드는 후보가 나왔을 때 재접속으로 다시 굴리는
악용이 가능하기 때문이다.

`chooser` 는 예외적으로 교체될 수 있다 (아래 이탈 처리 참고).

### 회차 리셋

전멸하면 `world` 폴더가 통째로 삭제되고 `TeamState` 가 새로 생성되므로
`ownedPerks` 와 `pending` 은 자동으로 비워진다. 별도 초기화 코드가 필요 없다.
`perksEnabled` 만 `TeamRosterStore` 가 유지하는 팀 명단과 함께 다음 회차로 넘긴다.

## 증강 정의 형식

`config/sharedfate-perks.json` 에 목록을 적는다.

```json
{
  "perks": [
    {
      "id": "sharedfate:tough_body",
      "name": "강골",
      "description": "팀 최대 체력 +2",
      "rarity": "silver",
      "effects": [
        { "type": "attribute", "attribute": "minecraft:max_health",
          "operation": "add_value", "amount": 2.0 }
      ]
    }
  ]
}
```

### 기본 효과 타입

| type | 필드 | 동작 |
|---|---|---|
| `attribute` | `attribute`, `operation`, `amount` | 팀원 전원에게 속성 수정자 부착 |
| `damage_dealt` | `multiplier` | 팀원이 주는 피해에 배율 |
| `damage_taken` | `multiplier` | 팀원이 받는 피해에 배율 |
| `status_effect` | `effect`, `amplifier` | 팀원 전원에게 상시 상태이상 |
| `mob_health` | `multiplier`, `targets`/`excludes` | 몹의 최대 체력에 배율 |
| `mob_damage` | `multiplier`, `targets`/`excludes` | 몹이 주는 피해에 배율 |
| `custom` | `handler` | Java 에 등록된 핸들러 호출 |

`custom` 은 위 여섯 가지로 표현할 수 없는 동작을 위한 확장점이다.
핸들러는 `PerkRegistry.registerCustom(id, handler)` 로 등록한다.

### 구간별 등급 배정

한 라운드에 등급이 섞이지 않는다. 구간마다 등급이 하나로 정해지고 그 등급에서만 3개를 뽑는다.

| 구간 | 등급 |
|---|---|
| 15렙 | **플레 고정.** 한 회차에 단 한 번뿐인 플레 라운드 |
| 5·10·20·25·30·35렙 | 실버 또는 골드를 50:50 무작위로 결정 |

정해진 등급에 후보가 모자라면 다른 등급에서 채운다. 우선순위는
실버→골드→플레, 골드→실버→플레, 플레→골드→실버 순이며,
버킷을 순서대로 소진해 3개를 최대한 채운다.

### 증강 목록

**미정.** 이 설계의 범위 밖이며 별도로 채운다.
구현 시에는 스키마 검증용 예시 몇 개만 넣고, 실제 풀은 비워 둔다.

## 흐름

```
팀 공유 레벨이 미처리 구간에 도달
  → PerkManager 가 감지, 접속 중인 팀원 중 무작위 1명 선정
  → 그 구간의 등급을 정하고 같은 등급에서 후보 3개 추첨, PendingOffer 로 확정 저장
  → 팀 전원에게 채팅 알림 "○○님에게 증강 선택권이 생겼습니다 (/shareteam perk)"
  → 뽑힌 사람이 /shareteam perk 실행
  → 서버가 PerkOfferPayload 전송, 클라이언트가 PerkOfferScreen 오픈
  → 선택 → PerkChoiceC2SPayload → 서버 검증 → ownedPerks 반영 → 효과 적용
  → PerkSyncPayload 로 팀 전원 동기화, 채팅으로 결과 공지
```

다른 팀원이 `/shareteam perk` 를 실행하면 같은 화면이 읽기 전용으로 열린다.

## 엣지 케이스

| 상황 | 처리 |
|---|---|
| 뽑힌 사람이 접속 종료·팀 탈퇴 | 접속 중인 다른 팀원에게 재추첨. `optionIds` 는 유지 |
| 팀원 전원 오프라인 | 대기 상태로 두고, 누군가 접속하면 그때 선정 |
| 대기 여러 개 | 구간 순서대로 하나씩 처리. 앞의 것을 골라야 다음이 열림 |
| 증강 미보유 상태에서 명령 실행 | "대기 중인 선택권이 없습니다" 안내 |
| 선택자가 아닌 사람이 선택 시도 | 서버에서 거부. 클라이언트 버튼도 비활성 |
| 이미 처리된 offer 에 중복 선택 | 서버에서 무시 (재전송·지연 패킷 방어) |
| JSON 파싱 실패 | 해당 증강만 건너뛰고 경고 로그. 시스템 전체는 계속 동작 |
| 이미 보유한 증강 | 그 회차 동안 후보에서 영구히 제외. 풀이 그만큼 줄어든다 |
| 후보를 3개 못 채움 | 가능한 개수만 제시. 0개면 그 구간은 건너뛰고 로그 |
| `perksEnabled == false` | 구간 감지 자체를 하지 않음 |
| 클라 모드 없는 접속 | 기존 `ClientModGate` 가 이미 차단. 별도 처리 불필요 |

## 오류 처리 원칙

증강 시스템의 실패가 본 게임을 망가뜨리면 안 된다.
JSON 오류, 알 수 없는 `type`, 없는 `handler` 는 모두 **해당 증강만 제외하고 경고 로그**로
처리하며 예외를 위로 던지지 않는다. 효과 적용 중 예외가 나면 그 효과만 건너뛴다.

## 테스트

기존 테스트가 `src/test/java/com/sharedfate/` 에 9개 있고 순수 로직 위주다.
증강 시스템도 같은 방식으로 게임 실행 없이 검증 가능한 부분을 테스트한다.

| 테스트 | 검증 내용 |
|---|---|
| `PerkMilestonesTest` | 구간 감지, 건너뛴 구간 누적, 레벨 하락 후 재상승 시 미발동, 35 초과 시 미발동, 구 3배수 데이터 유입 |
| `PerkDraftTest` | 구간별 등급 배정, 한 라운드 동일 등급, 보유 증강 영구 제외, 풀 고갈, 폴백 우선순위 |
| `PerkRegistryTest` | JSON 파싱, 잘못된 항목 건너뛰기, 알 수 없는 type·handler 처리 |
| `PerkStateCodecTest` | `TeamState` 왕복 직렬화, 증강 필드 없는 구 데이터 로드, 중첩 시절 `{perkId, count}` 형식 로드 |
| `PerkOfferLifecycleTest` | 선택자 이탈 시 재추첨, 중복 선택 거부, 대기열 순서 |

## 효과 타입 구현 상태

효과 타입 7종 모두 동작한다. 초기 구현에서 미뤄뒀던 두 항목은 해소됐다.

### `damage_dealt` / `damage_taken` — 해소됨

`LivingEntityPerkDamageMixin` 이 `LivingEntity.hurtServer` HEAD 에서 피해량 인자를
`@ModifyVariable` 로 갈아 끼운다. 받는 피해는 대상이 팀원일 때, 주는 피해는
`DamageSource.getEntity()` 가 팀원일 때 적용된다.

26.2 는 `Player extends Avatar extends LivingEntity` 구조이고 `Avatar` 는 `hurtServer` 를
재정의하지 않으므로, 피해 1건당 훅은 정확히 한 번만 탄다. `StatMirror` 는 체력 차분만
관측하므로 배율이 이미 반영된 결과를 보게 되어 이중 적용이 없다.

배율이 정확히 1.0 이면 인자를 그대로 반환한다. 증강 풀이 비어 있으면 바닐라와 동일하다.

### `status_effect` — 해소됨

`PerkStatusEffects` 가 증강이 부여한 상태이상을 판별해 `EffectSync` 의 공유 대상에서
제외한다. 판별 기준은 **무한 지속이면서 등급이 증강이 주는 등급 이하**인 인스턴스다.
포션·비컨·전도체는 무한 지속이 아니므로 구조적으로 걸러지지 않는다.

별도 추적 집합을 두지 않고 `TeamState.ownedPerks` 에서 매번 조회한다. 증강 목록이 이미
저장·리셋되는 단일 진실원이므로, 회차 리셋으로 목록이 비면 자동으로 무해해진다.

## 알려진 제약

밸런스를 잡거나 증강 풀을 채울 때 알고 있어야 할 항목들이다. 버그가 아니라 선택의 결과다.

| 항목 | 내용 |
|---|---|
| 아군 오사 배율 중첩 | 가해자의 주는 피해 배율과 피해자의 받는 피해 배율이 모두 곱해진다. 같은 팀이면 두 값이 같아 사실상 제곱이 된다 |
| 비 LivingEntity 대상 | 보트·마인카트·엔드 수정 등에는 주는 피해 배율이 걸리지 않는다. `Entity.hurtServer` 가 abstract 라 공통 훅 지점이 없다 |
| `damage_taken` 0.0 | 피해는 0 이 되지만 무적시간 20틱·피격 애니메이션·넉백은 그대로 발생한다 |
| 무한 지속 명령 | 팀이 같은 종류의 상태이상 증강을 보유 중이면 `/effect give <player> speed infinite` 로 건 것도 증강분으로 판정돼 공유되지 않는다. 포션을 절대 삼키지 않는 쪽을 택한 결과다 |
| 상태이상 재적용 1틱 지연 | 우유를 마셔 증강 상태이상이 지워지면 다음 틱에 다시 붙는다. 상태이상 표를 순회하는 도중 재적용하면 CME 위험이 있어 미룬다 |

## 범위 밖

- 증강 목록의 실제 내용
- 증강 효과의 밸런스 수치
- 증강 획득 이력을 회차 종료 후 보여주는 통계 화면
- 회차 간 메타 성장
