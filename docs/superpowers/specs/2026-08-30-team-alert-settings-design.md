# 피격·사망 알림을 팀 생성 시 정하는 설정으로 만들기

작성: 2026-08-30

## 무엇을 만드는가

팀을 처음 만들 때 리더가 **피격 알림**과 **사망 알림**을 각각 켜고 끌 수 있게 한다.
둘 다 기본값은 **끔**이고, 정한 뒤에는 바꿀 수 없다.

## 지금은 어떤가

| | 현재 |
|---|---|
| 피격 알림 | `DamageAlertHud` 가 좌하단에 「OOO 피격」을 30틱 띄운다. **끌 수 없다.** |
| 사망 알림 | 따로 없다. 전멸하면 `DeathHandler` 가 팀원 전원을 죽이므로 바닐라 사망 메시지가 **인원수만큼** 채팅에 뜬다. |
| 팀 설정 | 증강·최대 체력·교환 주기는 `sharedfate-team-roster.json` 으로 회차를 넘겨 이어진다. |
| 팀 만들기 화면 | 이름 칸 하나에 「증강 켜고 만들기」·「증강 없이 만들기」 두 단추. |

그래서 「1회차에 팀을 만들 때 정하면 그 뒤 회차에 그대로 따라온다」는 성질은 이미 있다.
새 설정 둘도 같은 자리에 얹는다.

## 정한 것

### 값이 사는 곳

`TeamState` 에 두 불리언을 더한다.

```java
/** 피격 알림 표시 여부. 팀을 만들 때 정하고 그 뒤로는 바꾸지 않는다. */
public boolean damageAlertEnabled;
/** 사망 알림 표시 여부. 팀을 만들 때 정하고 그 뒤로는 바꾸지 않는다. */
public boolean deathAlertEnabled;
```

둘 다 기본 `false`. **바꾸는 경로를 만들지 않는다** — 증강처럼 `/shareteam ... on|off` 가지를
두지 않고, 설정 탭에도 단추를 두지 않는다.

- **월드 저장**: `TeamState.CODEC` 에 `alerts` 묶음 하나를 덧붙인다. `PerkSection` 과 같은
  수법이다. 두 항목 모두 `optionalFieldOf` 이고 묶음 자체도 선택 항목이라, 이 필드가 없는
  기존 월드는 둘 다 꺼짐으로 읽힌다.
- **회차 넘김**: `TeamRosterStore.StoredSettings` 에 두 필드를 더하고 `FORMAT_VERSION` 을
  **3** 으로 올린다. 2 와 1 도 계속 읽고, 설정이 없으면 꺼짐으로 시작한다.

### 피격 알림

`StatMirror.collectDeltas` 가 `TeamBroadcaster.broadcastDamageAlert` 를 부르는 자리를
`state.damageAlertEnabled` 로 감싼다.

꺼지면 **서버가 패킷을 아예 보내지 않으므로 클라이언트는 손댈 것이 없다.** 표시 여부를
클라이언트에서 거르면 값을 클라이언트까지 옮기고 그쪽에서도 같은 판단을 해야 하는데,
판단이 두 곳으로 갈라지면 한쪽만 고쳐지는 사고가 난다.

### 사망 알림

**팀원의 바닐라 사망 메시지는 설정과 무관하게 언제나 막는다.** 전멸이 곧 인원수만큼의
채팅 도배라 어느 설정에서도 읽을 것이 못 된다. 사망 알림 설정이 정하는 것은
**게임 오버 화면에 이름을 한 줄 넣을지**뿐이다.

| | 끔 (기본) | 켬 |
|---|---|---|
| 채팅 사망 메시지 | 차단 | 차단 |
| 사망 화면 사인 줄 | 비어 있음 | 비어 있음 |
| 게임 오버 화면 부제 | 없음 | `OOO 님의 죽음으로 끝났습니다` |
| `'X' 팀이 전멸했습니다` 안내 | 그대로 (이름 없음) | 그대로 (이름 없음) |

#### 채팅 차단 방법

26.2 의 `ServerPlayer#die` 바이트코드를 확인했다. 메서드 맨 앞에서
`GameRules.SHOW_DEATH_MESSAGES` 를 한 번 읽고, 그 값 하나가 두 가지를 함께 정한다.

- `true` — 사인 문구를 만들어 `ClientboundPlayerCombatKillPacket` 에 실어 보내고,
  `PlayerList` 로 채팅에 방송한다.
- `false` — `CommonComponents.EMPTY` 를 실은 같은 패킷만 보낸다. 채팅 방송은 없다.

그래서 **이 한 호출만** Mixin 으로 가로채, 죽는 사람이 팀에 속해 있으면 `false` 를 돌려준다.
게임룰 자체는 건드리지 않는다 — 게임룰은 `level.dat` 에 저장되므로 잠깐 바꿨다 되돌리는
방식은 중간에 서버가 죽으면 그대로 남는다.

`GameRules#get` 은 `die` 안에서 두 번 불린다 (`SHOW_DEATH_MESSAGES`, 그리고 뒤쪽의
`FORGIVE_DEAD_PLAYERS`). **첫 번째 호출만** 대상으로 삼아야 한다.

#### 부제 전달 방법

새 S2C 묶음 `TeamWipePayload(String victimName)` 을 만든다. `DeathHandler` 가 전멸을
처리할 때, 팀이 사망 알림을 켰으면 **실제로 먼저 죽은 사람**의 이름을 실어 팀 전원에게
보낸다. 껐으면 보내지 않는다.

클라이언트는 `GameOverClientDisplay` 가 받아 사망 화면 부제로 넣는다. 지금 제목
(`게임 오버! · N회차`) 을 바꾸는 것과 같은 자리이고, `DeathScreenAccessor` 에 사인 줄을
바꾸는 접근자를 하나 더 연다.

**기존 `WorldResetPayload` 에 얹지 않는다.** 그 묶음은 `resetWorldOnTeamDeath` 가 켜져
있을 때만 나가는데, 테스트 서버는 이 값이 꺼져 있다. 얹으면 확인용 서버에서 사망 알림을
**확인할 방법이 없어진다.**

### 명령

`create` 의 `name` 은 greedyString 이라 뒤에 인자를 붙일 수 없다. 지금 증강을 다루는
방식을 그대로 늘린다.

```
/shareteam create [perks on|off] [damagealert on|off] [deathalert on|off] <이름>
```

순서는 고정이고 각 단계에서 이름으로 빠져나갈 수 있다. 그래서

- `/shareteam create 우리팀` — 셋 다 꺼짐 (예전과 같음)
- `/shareteam create perks on 우리팀` — 예전과 같음
- `/shareteam create perks on damagealert on deathalert off 우리팀` — 화면이 보내는 형태

가 모두 동작한다. 화면은 **언제나 완전한 형태**를 보낸다.

새 C2S 묶음을 만들지 않는 이유는 `TeamScreen` 문서에 적힌 것과 같다. 조작을 명령으로
태우면 권한 검사와 실패 문구가 언제나 한 곳에 있다.

### 화면

**팀 탭 (팀에 속하지 않았을 때)** — 토글 셋과 만들기 단추 하나로 바꾼다.

```
팀 이름 [___________________]

[ 증강      사용 안 함 ]
[ 피격 알림  끔        ]
[ 사망 알림  끔        ]

[      팀 만들기       ]

피격 알림과 사망 알림은 팀을 만들 때 정하며 나중에 바꿀 수 없습니다.
바꾸려면 팀을 해체하고 다시 만들어야 합니다. 증강은 설정 탭에서 바꿀 수 있습니다.
```

토글 셋의 값은 팀을 만들기 전이므로 서버에 없다. **화면이 들고 있다가** 만들기를 누를 때
명령 한 줄로 보낸다. 화면을 닫았다 열면 기본값(셋 다 끔)으로 돌아간다.

경고문은 **두 알림만** 가리킨다. 증강은 실제로 설정 탭에서 바뀌므로, 셋을 묶어
「바꿀 수 없다」고 적으면 아래에서 지우는 틀린 주석과 똑같은 거짓말을 화면에 새로
만드는 셈이 된다.

**설정 탭** — 두 값을 글자로만 보여 준다. 단추는 두지 않는다.

```
피격 알림 켜짐 / 꺼짐   (팀을 만들 때 정한 값)
사망 알림 켜짐 / 꺼짐   (팀을 만들 때 정한 값)
```

### 통신 규약

설정 탭이 값을 보여주려면 `TeamSyncPayload` 에 두 값이 실려야 한다. 그런데 이 묶음의
`StreamCodec.composite` 는 **이미 8개로 상한**이다.

그래서 `perksEnabled` 자리를 중첩 레코드 하나로 바꿔 한 칸에 셋을 담는다.

```java
public record Options(boolean perks, boolean damageAlert, boolean deathAlert) {
    public static final Options NONE = new Options(false, false, false);
    public static final StreamCodec<RegistryFriendlyByteBuf, Options> CODEC = ...;
}
```

`perksEnabled()` 는 `options.perks()` 를 돌려주는 편의 접근자로 남긴다. 부르는 곳을
전부 고치지 않아도 된다.

**통신 규약 12 → 13.** 서버와 클라이언트를 **함께** 갱신해야 한다.

### 지나가는 김에 고칠 것

사실과 다른 주석 둘을 **지운다.**

- `ShareTeamCommand.create` 위 — 「증강 사용 여부는 팀 생성 시에만 정해지고 그 뒤로는
  바꿀 수 없다」
- `TeamState.perksEnabled` 의 javadoc — 「팀 생성 시 리더가 정하고 그 뒤로는 바뀌지 않는다」

둘 다 틀렸다. `/shareteam perks on|off` 와 설정 탭의 「증강 켜기/끄기」로 실제로 바뀐다.
필드 이름이 이미 뜻을 다 담고 있으므로 대체 문구를 넣지 않고 지운다.

새로 넣는 두 필드의 주석은 남긴다. 그 둘은 **실제로** 바꿀 수 없어서 사실이다.

## 만지는 파일

| 파일 | 하는 일 |
|---|---|
| `team/TeamState.java` | 필드 둘, `alerts` 저장 묶음, 틀린 주석 삭제 |
| `sync/TeamRosterStore.java` | `StoredSettings` 확장, `FORMAT_VERSION` 3 |
| `team/TeamManager.java` | 복원 시 두 값 채우기 |
| `command/ShareTeamCommand.java` | `create` 문법 확장, 틀린 주석 삭제 |
| `sync/StatMirror.java` | 피격 알림 발송을 설정으로 감싸기 |
| `sync/DeathHandler.java` | 전멸 시 `TeamWipePayload` 발송 |
| `mixin/ServerPlayerDeathMessageMixin.java` | **새 파일.** 팀원의 사망 메시지 차단 |
| `net/TeamWipePayload.java` | **새 파일.** 부제용 S2C |
| `net/TeamSyncPayload.java` | `Options` 중첩 레코드 |
| `net/TeamBroadcaster.java` | `Options` 를 채워 보내기 |
| `net/SharedFateNetworking.java` | 새 묶음 등록, `PROTOCOL_VERSION` 13 |
| `client/ClientTeamState.java` | `Options` 읽기 |
| `client/team/TeamScreen.java` | 만들기 화면 토글 셋, 설정 탭 표시 |
| `client/GameOverClientDisplay.java` | 부제 넣기 |
| `client/mixin/DeathScreenAccessor.java` | 사인 줄 접근자 추가 |
| `resources/sharedfate.mixins.json` | 새 Mixin 등록 |

## 시험

- `TeamState` 왕복 — 두 값이 저장·복원되는지, 없는 저장이 꺼짐으로 읽히는지
- `TeamRosterStore` 왕복 — 형식 3 왕복, 형식 2·1 파일이 꺼짐으로 읽히는지
- `TeamSyncPayload` 왕복 — `Options` 세 값이 그대로 오가는지
- `ShareTeamCommand` — 여섯 가지 `create` 형태가 모두 파싱되고 값이 맞는지
- `StatMirror` — 피격 알림이 꺼진 팀에 패킷이 나가지 않는지

Mixin 과 화면은 단위 시험으로 확인할 수 없다. **실제 플레이로 봐야 한다** —
전멸 시 채팅에 사망 메시지가 없는지, 사망 알림을 켠 팀에서 부제가 뜨는지.

## 하지 않는 것

- 증강을 팀 생성 뒤 못 바꾸게 막기 — 지금 동작을 바꾸는 일이라 범위 밖이다
- 서버 `config` 에 기본값 추가 — 팀마다 정하는 값이라 서버 기본값이 필요 없다
- 피격 알림의 **표시 방식** 변경 (위치·색·지속) — 켜고 끄는 것만 다룬다
