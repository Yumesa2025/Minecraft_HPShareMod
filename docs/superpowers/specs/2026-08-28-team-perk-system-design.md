# 팀 증강(Perk) 시스템 설계

- 상태: 승인됨
- 대상 버전: 0.5.0-dev
- 작성일: 2026-08-28

## 요약

팀 공유 레벨이 3의 배수에 처음 도달할 때마다, 팀원 중 무작위로 뽑힌 한 명이
증강 3개 중 하나를 고른다. 고른 효과는 팀 전체에 적용되고 그 회차 내내 유지된다.
전멸로 회차가 넘어가면 전부 초기화된다.

증강 목록 자체는 이 설계의 범위 밖이다. 이 문서는 증강을 담을 **틀**만 정의한다.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 발동 조건 | 팀 공유 레벨(`TeamState.xpLevel`)이 3·6·9·…·36에 **처음** 도달 |
| 구간 수 | 12회 (36렙 이후로는 없음) |
| 선택자 | 접속 중인 팀원 중 무작위 1명 |
| 효과 범위 | **팀 전체** |
| 효과 성격 | 상시 패시브(로그라이트형) + 장단점 교환(트레이드오프형) 혼합 |
| 창 열기 | 자동으로 열지 않음. 알림 후 본인이 `/shareteam perk` 로 직접 연다 |
| 관전 | 다른 팀원이 열면 같은 화면을 읽기 전용으로 본다 |
| 켜고끄기 | 팀 생성 시 리더가 결정, 그 뒤 변경 불가 |
| 회차 이월 | 전부 초기화 (`perksEnabled` 만 유지) |
| 후보 구성 | 등급제. 구간이 오를수록 고등급 확률 상승 |
| 중복 | 증강별 `stackable` 속성. 불가능한 것은 한 번 고르면 풀에서 제외 |
| 정의 위치 | `config/sharedfate-perks.json` + Java 핸들러 하이브리드 |

## 발동 조건 상세

`TeamState.lastPerkMilestone` 에 마지막으로 처리한 구간을 기록한다.
현재 `xpLevel` 이 `lastPerkMilestone` 보다 큰 3의 배수에 도달하면 그 구간을 발동시키고
`lastPerkMilestone` 을 갱신한다.

경험치를 써서 레벨이 내려갔다가 다시 올라와도 재발동하지 않는다.
레벨이 한 번에 여러 구간을 건너뛰면(예: 2렙 → 9렙) 건너뛴 구간마다 각각 발동해
대기열에 순서대로 쌓인다.

## 아키텍처

새 기능은 `perk/` 패키지로 격리한다. 기존 파일 수정은 최소화한다.

```
src/main/java/com/sharedfate/perk/
  Perk.java              증강 정의 (id, 이름, 설명, 등급, 중첩 여부, 최대 중첩, 효과 목록)
  PerkRarity.java        enum COMMON / RARE / EPIC
  PerkEffect.java        효과 인터페이스 (apply / remove)
  PerkEffectType.java    JSON type 문자열 → 효과 팩토리 매핑
  effect/AttributeEffect.java
  effect/DamageDealtEffect.java
  effect/DamageTakenEffect.java
  effect/StatusEffectPerk.java
  effect/CustomEffect.java
  PerkRegistry.java      JSON 로드 + Java 커스텀 핸들러 등록을 합쳐 id→Perk 조회
  PerkDraft.java         등급 가중치와 중복 규칙에 따라 후보 3개 추첨
  PerkManager.java       구간 감지 → 추첨 → 대기열 → 적용 (서버 틱)
  PerkStack.java         record {String perkId, int count}
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
| `SharedFateNetworking.java` | 페이로드 3개 등록, `PROTOCOL_VERSION` 5 → 6 |
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
public int lastPerkMilestone;             // 마지막 처리 구간 (0, 3, 6, …, 36)
public final List<PerkStack> ownedPerks;  // 중첩은 count 로 표현
public final List<PendingOffer> pending;  // 대기 중인 선택권, 여러 개 가능
```

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
      "rarity": "common",
      "stackable": true,
      "maxStacks": 3,
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
| `custom` | `handler` | Java 에 등록된 핸들러 호출 |

`custom` 은 위 네 가지로 표현할 수 없는 동작을 위한 확장점이다.
핸들러는 `PerkRegistry.registerCustom(id, handler)` 로 등록한다.

### 등급 가중치

| 구간 | COMMON | RARE | EPIC |
|---|---|---|---|
| 3·6·9·12 | 75% | 25% | 0% |
| 15·18·21·24 | 40% | 50% | 10% |
| 27·30·33·36 | 15% | 50% | 35% |

### 증강 목록

**미정.** 이 설계의 범위 밖이며 별도로 채운다.
구현 시에는 스키마 검증용 예시 몇 개만 넣고, 실제 풀은 비워 둔다.

## 흐름

```
팀 공유 레벨이 미처리 구간에 도달
  → PerkManager 가 감지, 접속 중인 팀원 중 무작위 1명 선정
  → 등급 가중치로 후보 3개 추첨, PendingOffer 로 확정 저장
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
| `PerkMilestoneTest` | 구간 감지, 건너뛴 구간 누적, 레벨 하락 후 재상승 시 미발동, 36 초과 시 미발동 |
| `PerkDraftTest` | 등급 가중치 분포, 중복 제외, `stackable` 처리, `maxStacks` 도달 시 제외, 후보 부족 상황 |
| `PerkRegistryTest` | JSON 파싱, 잘못된 항목 건너뛰기, 알 수 없는 type·handler 처리 |
| `PerkStateCodecTest` | `TeamState` 왕복 직렬화, 증강 필드 없는 구 데이터 로드 |
| `PerkOfferLifecycleTest` | 선택자 이탈 시 재추첨, 중복 선택 거부, 대기열 순서 |

## 알려진 미해결 항목

증강을 담는 틀은 완성됐지만, 효과 타입 5종 중 아래 셋은 실제 증강 풀을 채우기 전에
추가 작업이 필요하다. 지금은 풀이 비어 있어 드러나지 않는다.

### `damage_dealt` / `damage_taken` — 피해 계산 후킹 없음

배율을 계산하는 부분(`PerkManager.damageDealtMultiplier` / `damageTakenMultiplier`)까지는
있지만, 그 값을 실제 피해 계산에 반영하는 지점이 없다. 기존 코드의 피해 관련 훅은
`ServerLivingEntityEvents.AFTER_DAMAGE` 뿐이고 이건 사후 통지라 수치를 바꿀 수 없다.
피해 계산 지점에 mixin 을 새로 넣어야 한다.

### `status_effect` — EffectSync 와 간섭

`EffectSync.tick` 이 대표 플레이어의 활성 상태이상을 통째로 `TeamState.effects` 로 복사해
팀에 공유한다. `status_effect` 증강으로 건 무한 지속 상태이상도 여기에 딸려 들어가므로,
회차가 바뀌어 증강을 잃은 뒤에도 `TeamState.effects` 에 남아 되살아날 수 있다.
증강이 부여한 상태이상을 `EffectSync` 의 수집 대상에서 제외하는 처리가 필요하다.

## 범위 밖

- 증강 목록의 실제 내용
- 증강 효과의 밸런스 수치
- 증강 획득 이력을 회차 종료 후 보여주는 통계 화면
- 회차 간 메타 성장
