# SharedFate

Minecraft Java Edition 26.2 / Fabric용 협동 모드입니다. 최대 4명이 인벤토리·장비·체력·
배고픔·경험치·포션 효과를 공유하고, 아군이 선택한 핫바 칸을 빨간 테두리로 표시합니다.
팀 전멸 뒤에는 다음 회차의 새 월드를 시작하며, 엔더 드래곤을 처치하면 승리와 엔딩으로
마무리됩니다.

> 현재 버전은 **`0.4.0-dev` 사전 배포판**입니다. 서버와 모든 클라이언트는 반드시 같은
> SharedFate 버전을 사용해야 합니다.

## 다운로드

| 파일 | 용도 |
|---|---|
| [SharedFate-0.4.0-dev-client.zip](https://github.com/Yumesa2025/Minecraft_HPShareMod/releases/download/v0.4.0-dev/SharedFate-0.4.0-dev-client.zip) | 일반 플레이어 권장. SharedFate와 호환 Fabric API, 설치 안내 포함 |
| [sharedfate-0.4.0-dev.jar](https://github.com/Yumesa2025/Minecraft_HPShareMod/releases/download/v0.4.0-dev/sharedfate-0.4.0-dev.jar) | 서버 운영자·수동 설치용 모드 JAR |
| [SHA256SUMS.txt](https://github.com/Yumesa2025/Minecraft_HPShareMod/releases/download/v0.4.0-dev/SHA256SUMS.txt) | 다운로드 무결성 확인 |

[사전 배포판 설명과 모든 자산 보기](https://github.com/Yumesa2025/Minecraft_HPShareMod/releases/tag/v0.4.0-dev)

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

주손 선택 슬롯과 위치는 기본적으로 개인별입니다. 팀 가입 시 개인 아이템은 현재 위치에
드랍되고 개인 경험치는 팀 공유 풀에 합쳐집니다. 일반 탈퇴자는 빈 인벤토리와 경험치 0으로
분리됩니다.

## 명령어

```text
/shareteam help
/shareteam create <팀 이름>
/shareteam invite <플레이어>
/shareteam invites
/shareteam accept <팀 이름>
/shareteam decline <팀 이름>
/shareteam status
/shareteam health <20~40>
/shareteam swap on <1~120분>
/shareteam swap off
/shareteam swap status
/shareteam list
/shareteam leave
/shareteam disband confirm
```

## 사전 배포판의 한계

이번 버전은 실제 네더·엔드 왕복, 화로와 상자 경계의 쉬프트 클릭,
크리에이티브 세부 조작, `KEEP_INVENTORY`, 동시 엔더상자, 3~4인 장시간 프리즘이의 수동 확인이
남아 있는 사전 배포판입니다.

## 소스 빌드

JDK 25에서 실행합니다.

```powershell
.\gradlew.bat build --no-daemon
```

`JAVA_HOME`이 JDK 25를 가리키도록 설정하세요. 또는 Gradle 실행 시
`-Dorg.gradle.java.home=<JDK 25 경로>`를 지정할 수 있습니다.

MIT License입니다.
