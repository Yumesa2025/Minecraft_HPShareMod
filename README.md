# SharedFate

Minecraft Java Edition 26.2 / Fabric용 협동 모드입니다. 최대 4명이 인벤토리·장비·체력·
배고픔·경험치·포션 효과를 공유하고, 아군이 선택한 핫바 칸을 빨간 테두리로 표시합니다.
팀 공유 레벨이 오르면 팀 전체에 적용되는 **증강**을 함께 고르고, 팀 전멸 뒤에는 다음 회차의
새 월드를 시작하며, 엔더 드래곤을 처치하면 승리와 엔딩으로 마무리됩니다.

> 현재 버전은 **`0.7.0-dev` 사전 배포판**입니다. 서버와 모든 클라이언트는 반드시 같은
> SharedFate 버전을 사용해야 합니다.
>
> **0.6.0-dev 이하에서 올라오는 경우 서버와 모든 클라이언트를 함께 갱신하세요.** 통신 규약이
> 12로 바뀌어 예전 클라이언트로는 접속할 수 없습니다.
> (참고: 0.4.0-dev 는 5, 0.5.x 는 10, 0.6.0-dev 는 11입니다.)

## 다운로드

| 파일 | 용도 |
|---|---|
| [SharedFate-0.7.0-dev-client.zip](https://github.com/Yumesa2025/Minecraft_HPShareMod/releases/download/v0.7.0-dev/SharedFate-0.7.0-dev-client.zip) | 일반 플레이어 권장. SharedFate와 호환 Fabric API, 설치 안내 포함 |
| [sharedfate-0.7.0-dev.jar](https://github.com/Yumesa2025/Minecraft_HPShareMod/releases/download/v0.7.0-dev/sharedfate-0.7.0-dev.jar) | 서버 운영자·수동 설치용 모드 JAR |
| [SHA256SUMS.txt](https://github.com/Yumesa2025/Minecraft_HPShareMod/releases/download/v0.7.0-dev/SHA256SUMS.txt) | 다운로드 무결성 확인 |

[사전 배포판 설명과 모든 자산 보기](https://github.com/Yumesa2025/Minecraft_HPShareMod/releases/tag/v0.7.0-dev)

### 클라이언트 설치

1. Minecraft 26.2와 Fabric Loader 0.19.3 이상을 설치합니다.
2. 게임과 런처를 완전히 종료합니다.
3. 클라이언트 ZIP을 풀어 안내 파일을 읽습니다.
4. `%appdata%\.minecraft\mods`에서 이전 `sharedfate-*.jar`를 제거합니다.
5. ZIP의 `mods` 폴더 안 JAR 두 개를 게임의 `mods` 폴더에 복사합니다.
6. Fabric 프로필로 게임을 실행해 같은 버전의 서버에 접속합니다.

Java 25가 필요합니다. SharedFate JAR만 받았다면 Fabric API
`0.156.0+26.2` 이상도 직접 설치해야 합니다.

### 업데이트와 제거

- 업데이트: 게임을 종료한 뒤 이전 SharedFate JAR를 새 JAR로 교체합니다. 여러 버전을 동시에
  두지 마세요.
- 제거: 서버를 정상 종료하고 월드를 백업한 뒤 서버와 모든 클라이언트에서 SharedFate JAR를
  제거합니다. 공유 상태가 들어 있는 운영 월드를 바닐라로 바로 열지 않는 것을 권장합니다.

## 서버 운영

서버에도 Fabric Loader, Fabric API와 동일한 SharedFate JAR가 필요합니다. 자동 회차 서버는
모드가 월드를 직접 삭제하지 않습니다. 팀 전멸 시 서버가 정상 저장·종료한 뒤
`scripts/sharedfate-server-loop.ps1`이 표식에 기록된 서버 직속 월드 한 폴더만 검증해
삭제하고 다시 시작합니다.

운영 월드는 먼저 백업하세요. 전체 서버 폴더, 심볼릭 링크·정션, 표식과 일치하지 않는 경로는
초기화 대상으로 사용하면 안 됩니다. 표식 파일은 서버 루트에 있어야 하고, 표식에 기록된
월드 경로는 서버 루트의 일반 하위 폴더 하나여야 합니다.

## 주요 기능

- 공유 저장공간 63칸: 핫바 9 + 메인 54
- 방어구 4칸, 오프핸드, 엔더상자 공유
- 팀별 최대 체력 20~40, 흡수 체력, 배고픔·포화도·경험치·포션 효과 공유
- 아군 선택 슬롯 빨간 표시와 피격 알림·피격 연출
- 주기적 무작위 위치 교환과 음식 초과 영양 버퍼
- 팀 전멸 시 안전한 다음 회차 시작, 드래곤 승리와 피해 통계 책
- **팀 증강**: 공유 레벨 5·10·15·20·25·30·35 구간마다 팀원 한 명이 3개 중 하나를 고르고
  효과는 팀 전체에 적용됩니다. 등급은 실버·골드·프리즘이며 15렙은 프리즘 고정입니다

주손 선택 슬롯과 위치는 기본적으로 개인별입니다. 팀 가입 시 개인 아이템은 현재 위치에
드랍되고 개인 경험치는 팀 공유 풀에 합쳐집니다. 일반 탈퇴자는 빈 인벤토리와 경험치 0으로
분리됩니다.

## 명령어

대부분은 `/shareteam` 창에서 할 수 있습니다. 명령으로도 그대로 됩니다.

```text
/shareteam                      팀 화면 열기 (모드가 있는 클라이언트)
/shareteam help
/shareteam create <팀 이름>
/shareteam create perks <on|off> <팀 이름>
/shareteam invite <플레이어>     상대를 곧바로 팀에 넣습니다 (리더)
/shareteam perks <on|off>        증강 사용 여부 (리더)
/shareteam health <20~40>        공유 최대 체력 (리더)
/shareteam swap on <1~120분>
/shareteam swap off
/shareteam swap status
/shareteam perk
/shareteam perk list
/shareteam status
/shareteam list
/shareteam leave
/shareteam disband confirm
```

**초대에 수락 절차가 없습니다.** 리더가 `invite` 를 치면 상대가 곧바로 들어옵니다.
그때 상대의 개인 아이템은 있던 자리에 드랍되고 개인 경험치는 공유 풀에 합쳐지므로,
부르기 전에 미리 알려 주는 편이 좋습니다.

증강은 기본으로 꺼져 있습니다. `/shareteam create <팀 이름>` 으로 만들면 증강 없이
시작하고, 켜려면 `/shareteam create perks on <팀 이름>` 으로 만들거나 나중에
`/shareteam perks on` 으로 켭니다.

## 팀 화면

`/shareteam` 을 인자 없이 치면 창이 열립니다. 탭은 넷입니다.

| 탭 | 하는 일 |
|---|---|
| 현황 | 팀 이름, 접속 인원, 팀 레벨, 다음 증강까지 남은 레벨, 공유 체력, 교환 주기, 증강 사용 여부 |
| 팀 | 팀 만들기, 접속자별 초대, 나가기·해체 |
| 설정 | 최대 체력, 위치 교환, 증강 켜고 끄기 — **리더만** |
| 증강 | 보유 증강 목록, 증강 선택창 열기 |

## 증강 설정

증강 목록은 서버의 `config/sharedfate-perks.json` 에서 편집합니다. 파일이 없으면 서버가
켜질 때 기본 풀(실버 14 · 골드 18 · 프리즘 15)이 자동으로 만들어집니다. 지우면 다음 실행에
다시 생깁니다.

한 구간에는 같은 등급 후보 3개만 나오고, **한 번 고른 증강은 그 회차 동안 다시 나오지
않습니다.** 그래서 등급마다 최소 8개(프리즘은 3개)는 남겨 두어야 6개 구간을 모두 채울 수
있습니다. 모자라면 실버→골드→프리즘 순으로 다른 등급이 채웁니다.

## 사전 배포판의 한계

이번 버전은 실제 네더·엔드 왕복, 화로와 상자 경계의 쉬프트 클릭,
크리에이티브 세부 조작, `KEEP_INVENTORY`, 동시 엔더상자, 3~4인 장시간 플레이의 수동 확인이
남아 있는 사전 배포판입니다.

증강 쪽은 2인 플레이로 모두 확인했습니다. 「요새 탐지기」의 네더 나침반, 「허공답보」의
공중 점프, 「장님 거인」의 체력·허기 UI 숨김, 「본진이 바뀐다」·「뿌리내린 발」의 위치 교환이
모두 의도대로 동작합니다.

## 소스 빌드

JDK 25에서 실행합니다.

```powershell
.\gradlew.bat build --no-daemon
```

`JAVA_HOME`이 JDK 25를 가리키도록 설정하세요. 또는 Gradle 실행 시
`-Dorg.gradle.java.home=<JDK 25 경로>`를 지정할 수 있습니다.

MIT License입니다.
