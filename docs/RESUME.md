# 새 세션에서 이어서 작업하기

기록일: 2026-10-09 (Asia/Seoul). 저장소: [99jinwoo/twilight](https://github.com/99jinwoo/twilight).

이 문서는 대화를 보관한 뒤 Codex·Claude Code가 같은 저장소에서 재개하기 위한 인수인계다. 기록 시점의 상태이므로 시작할 때 원격을 다시 확인한다. 세션 종료는 제품의 모든 요구사항 완료를 뜻하지 않는다.

## 1. 시작 순서

1. 루트 [AGENTS.md](../AGENTS.md), 이 문서, [STATUS](STATUS.md), [SPEC](SPEC.md), [DECISIONS](DECISIONS.md)를 읽는다. `CLAUDE.md`도 같은 공통 규칙과 문서를 참조한다. 원본 `HANDOVER.md`보다 최신 사양과 사용자 지시가 우선한다.
2. 아래 조회를 수행해 다른 창의 변경·열린 PR·최신 배포본을 확인한다. 미커밋 작업이 있으면 보존하고, 오래된 로컬 main을 기준으로 새 기능이나 버전을 만들지 않는다.
3. 새 과업은 최신 `origin/main`에서 `feat/<주제>` 브랜치를 만든다. 기존 PR을 이어가라는 요청이면 그 PR의 최신 브랜치를 사용한다. 앱 코드 변경 후 필수 검사를 수행하고 PR로 제공한다. 머지는 사용자가 결정한다.

```sh
git status --short --branch
git fetch --all --prune
git log -5 --oneline origin/main
gh pr list --repo 99jinwoo/twilight --state open
gh release list --repo 99jinwoo/twilight --limit 5
```

새 창의 첫 요청 예시:

> Twilight 다음 작업: [증상이나 원하는 기능]. AGENTS.md와 docs/RESUME.md를 읽고 원격 main·열린 PR·최신 릴리스부터 확인한 뒤 새 브랜치와 PR로 진행해줘.

## 2. 보관 직전 기준 상태

| 항목 | 확인한 내용 |
|---|---|
| 병합 | [PR #1](https://github.com/99jinwoo/twilight/pull/1), [PR #2](https://github.com/99jinwoo/twilight/pull/2) 모두 병합됨 |
| 앱 구현 기준 main | `f5b186b7c66f488091cbbeaa1da3fa4df4b67eb0` (PR #2 merge). 이후 문서 PR은 앱을 바꾸지 않음 |
| 최신 배포 | [v0.3.2-preview](https://github.com/99jinwoo/twilight/releases/tag/v0.3.2-preview), versionCode **5** |
| 배포 태그 커밋 | `1cfb461d558ef0cbf479fd69d8c0a8e68868caff` (검증 결과 문서 포함) |
| APK를 만든 코드 | `f2be5ce95a2970dde2626c0564edd5b2730aec75` |
| APK 검사 실행 | [CI 37864244374](https://github.com/99jinwoo/twilight/actions/runs/37864244374), 성공 |
| 병합 후 검사 실행 | [CI 37924663898](https://github.com/99jinwoo/twilight/actions/runs/37924663898), 성공. 이 실행의 새 APK를 재배포한 것은 아님 |
| 작업 가지 정리 | 이 문서 작업을 시작할 때 원격은 main 하나, 열린 PR 없음. 예전 두 feat 브랜치는 삭제됨 |
| 자동 삭제 설정 | `delete_branch_on_merge=true` 확인. 이후 문서 PR의 병합 여부는 GitHub에서 다시 확인 |
| 사용자 설치본 | v0.3.2 APK를 제공했으나 실제 설치 버전·수정 후 결과는 별도 확인 필요 |

main의 앱·빌드·검사 파일이 v0.3.2 태그와 같음을 확인했다. 파일이 같아도 다시 빌드한 APK의 바이트·서명까지 같다는 뜻은 아니다.

## 3. 유지할 제품 결정

- 한국 개인용 Android 앱, minSdk 29(Android 10). Galaxy S24+와 Note9을 고려한다. 다른 저장소의 기기 정보만으로 이 앱의 실기기 검증을 완료 처리하지 않는다.
- 크기는 **1×1·가로 2×1·세로 1×2**다. 4×1은 사용자가 오타라고 정정했다.
- 1×1은 기온·날씨 이모지·`미`/`초미` 각각 색·선택한 박명의 직전/다음 시각을 유지한다. 다음 박명은 위·굵게, 직전은 아래·작고 흐리게. 어제/오늘/내일을 붙인다. 추가 정보를 억지로 넣지 않는다.
- 가로 2×1은 좌측 현재 정보와 우측 **네 시간 가로 열**을 유지한다. 좁은 화면도 세로 목록으로 바꾸지 않는다. 세로 1×2는 예보가 아래에 온다. 설치 위젯 편집은 런처가 알려 준 크기·방향을 사용한다.
- 박명은 API에서 읽지 않고 기기에서 계산한다. 시민 −6°·항해 −12°·천문 −18°를 구분하고 BMNT/EENT는 항해박명 명칭으로 쓴다. 인터넷의 일몰·시민박명이나 Google AI 요약과 비교할 때 사건·날짜·좌표부터 맞춘다.
- 기온은 기상청 실황, 먼지는 에어코리아 측정소 관측값이다. 실측 결측을 예보로 채우지 않는다. 예보 하늘상태를 사용한 아이콘은 `예`로 구분한다. 샘플은 별도 선택했을 때만 전체에 적용한다.
- **현재 위치 갱신은 선택 사항**으로 앱 실행·전경 복귀·새로고침 때만 요청한다. 백그라운드 위치 추적은 하지 않고, 닫힌 동안에는 마지막 좌표로 날씨를 갱신한다.
- **측정소 자동 선택은 기본**이며 고정 지역에도 적용한다. 직접 입력/목록 선택은 접힌 선택 사항이다. 시군구·시도 주소 필터로 후보를 받아 두 먼지를 측정하는 가까운 곳을 선택한다. 행정 경계 바깥까지 포함한 전국 절대 최근접을 보장하지 않는다.
- 측정소 메타데이터는 성공한 날 재사용한다. 실패는 1분 뒤 재시도할 수 있고, 전경에서는 65초 간격 최대 두 번 자동 재시도한다(인증·한도 오류 제외). 같은 장소의 일시 실패는 마지막 측정소를 유지하고, 1km 넘는 위치 이동은 이전 측정소를 비워 다른 지역 관측이 섞이지 않게 한다.
- 기본 날씨/위젯 갱신은 1시간이다. 2시간 넘은 관측은 위젯에서 비우고 앱 상세에 시각과 함께 보존한다. OS 때문에 갱신과 박명·자정 전환이 지연될 수 있다.
- 키는 사용자가 앱에 입력하고 기기 내부에 암호화 저장한다. 코드·APK·채팅·로그에 실제 키를 넣지 않는다. **맥 중계서버·일반 공개 준비는 사용자가 후속 과업으로 미뤘다.**

## 4. 검증된 범위와 남은 확인

- 배포 코드의 로컬·CI 빌드, 단위 검사 **44개(debug/release 각각)** 통과. CI lint 오류 0 / 경고 24(로컬23).
- Android 10/API 29와 Android 15/API 35 에뮬레이터 각각 **13개** 기기 검사 통과. 측정소 무조작 자동 선택/조회, 실패/재시도/지역 대안/캐시, 수동 설정 격리, 위치 흐름, 저장과 렌더링을 검사했다.
- 샘플 9개 크기와 합성 관측·실제 박명 7개 크기에서 위젯을 검사했다. 좁은 130×72·140×72dp의 네 열, 자정 이후 날짜, 음수 기온을 확인했다.
- [박명 독립 검증](ASTRONOMY_VALIDATION.md): USNO 108개 시각과 최대 30초 차이. 확인한 좌표·날짜 밖의 보편적 정확도를 보증하지 않는다.
- 사용자 보고로 Note9의 초기 v0.1 동작, 이후 기상청·먼지 실측 수신을 확인했다. 2026-10-09 화면에서는 실제 위치·주소·두 먼지의 오늘/내일 예보를 확인했다.
- **v0.3.2의 S24+ 실서비스 시간 초과 복구와 가독성은 미확인**이다. 자동 검사의 합성 응답·공개 시험 좌표를 실제 인증·GPS·삼성 런처 검증으로 표현하지 않는다.

다음 버그 제보에서는 앱 버전, 위젯 크기/홈 격자, 고정/현재 위치 모드, 측정소 자동/직접 선택 상태, 안전한 오류 문구·관측/조회 시각을 확인한다. 키는 받지 않으며 개인 좌표가 담긴 원본 화면을 공개 저장소에 올리지 않는다.

## 5. 배포·업데이트 시 주의할 실제 상태

[v0.3.2 APK](https://github.com/99jinwoo/twilight/releases/download/v0.3.2-preview/twilight-0.3.2-preview.apk)는 34,772,791바이트다.

```text
SHA-256: 2a7d59f50d181a366dc75620b6558f95a460a26c6a345bdf149d765f92ae23c3
```

- 지금은 **고정 배포 서명 미도입**이다. v0.3.1과 v0.3.2는 서명이 달라 삭제 후 재설치를 안내했다. API 키·설정·위젯도 다시 구성해야 한다. 같은 버전을 다른 CI 실행에서 빌드해도 덮어 설치를 보장하지 않는다.
- 고정 서명키 생성·GitHub Secrets 업로드는 이전 작업에서 자동 승인 검토가 거부해 수행하지 않았다. 이 문서는 재시도 승인이 아니다. 다시 진행할 때 사용자에게 구체적인 보관·복구·업로드 방식을 설명하고 명시적 승인을 받는다. 다른 앱의 서명키를 복사하지 않는다.
- 현재 기준 다음 APK는 **versionCode 6 이상**이다. 원격이나 설치본이 더 앞서 있으면 그보다 큰 값으로 정한다. 버전 증가만으로 서명 불일치가 해결되지는 않는다. 문서 수정만으로는 앱 버전을 올리지 않는다.
- 검사를 통과한 동일 APK를 Releases에 올리고 코드 SHA·CI·해시를 함께 남긴다. 태그·기존 릴리스 자산을 임의로 덮어쓰지 않는다. 소스 ZIP과 합성 검사 기록/화면 ZIP도 v0.3.2 릴리스에 있다.
- GitHub Actions 산출물 보관은 현재 14일이다. 나중에 CI 파일이 만료되면 릴리스의 검증 ZIP을 참조한다. 이 대화나 특정 맥의 임시 폴더가 유일한 근거가 되지 않게 한다.

## 6. 빌드와 코드 위치

JDK 17, Gradle Wrapper 8.13, Android SDK Platform 36, Build Tools 35.0.0. SDK 경로는 각 환경의 `ANDROID_HOME` 또는 커밋하지 않는 `local.properties`에 지정한다. 개인 날씨 키 없이 빌드·합성 검사가 가능하다.

```sh
./gradlew lint test assembleDebug assembleDebugAndroidTest
# 연결된 Android 10 이상 기기/에뮬레이터가 있을 때
./gradlew connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. [CI](../.github/workflows/android.yml)는 API 29/35에서 검사한다. 이전 맥 arm64 에뮬레이터는 SIGILL로 실패해 Linux CI를 사용했다. 같은 환경이 막히면 실패를 기록하고 검증 가능한 환경을 사용한다. [기기 검사 스크립트](../scripts/run-device-tests.sh)는 CI가 내려받은 `built/outputs/apk/` 경로를 전제로 하므로 로컬에 그대로 실행하지 않는다.

다음 표의 소스 경로는 `app/src/main/java/com/jinwoo/twilightandyou/` 기준이다.

| 상황 | 먼저 볼 파일 |
|---|---|
| 홈 위젯 크기·글자·예보 배치 | `widget/TwilightWidget.kt`, `model/WidgetModel.kt` |
| 미리보기·설정 | `ui/TwilightScreen.kt`, `MainActivity.kt` |
| 새로고침·측정소 자동 선택 UI | `ui/LiveDataPanel.kt` |
| 공급자 조회·재시도·캐시 | `data/LiveRepository.kt`, `data/remote/PublicWeatherApi.kt` |
| JSON 해석·결측·발표본 | `data/remote/ApiParsers.kt`, `model/LiveData.kt` |
| 측정소 검색 범위 | `model/StationSelection.kt` |
| 위치 획득·주소·예보권역 | `data/ManualLocation.kt`, `data/LocationDescription.kt`, `model/AutomaticLocation.kt` |
| 위젯별 저장·자동 갱신 | `data/SettingsStore.kt`, `widget/WidgetRefreshWorker.kt`, `widget/PinWidgetReceiver.kt` |
| 키 암호화 | `data/ApiKeyStore.kt` |
| 천문 계산 | `astronomy/SolarCalculator.kt` |

JVM 검사는 `app/src/test/`, 기기 검사는 `app/src/androidTest/`에 있다. 위젯 크기 회귀는 `WidgetRenderingTest`, 자동 측정소는 `AirStationFlowTest`·`AutomaticStationRepositoryTest`, 위치와 저장 격리는 `AutomaticLocationFlowTest`·`SettingsStoreTest`를 참조한다.

## 7. 다음 과업 후보 — 아직 착수 승인된 작업 목록이 아님

1. 새 사용자 보고가 있으면 v0.3.2의 실제 측정소 자동 선택·위젯 가독성부터 확인하고 수정한다.
2. 반복 재설치를 줄일 고정 배포 서명과 복구 가능한 키 보관 방식을 결정한다.
3. 재부팅·절전·장시간/7일 사용·다중 위젯 갱신을 실제 기기에서 확인한다. 박명 사건·자정 전환 지연도 포함한다.
4. 남은 첫 출시 요구: 박명 전체 표/일출몰 상세, 오늘·내일 최고최저, 갱신 간격 선택, 시스템 테마·0% 투명도·먼지색 재정의. SPEC에 적혀 있다는 이유만으로 구현 완료로 보지 않는다.
5. 맥 서버·공개 배포·인증키 중앙 관리 등은 사용자 요청이 돌아왔을 때 별도 설계한다.

현재 완료된 것은 v0.3.2 수정·검증·배포·PR #2 병합과 이번 재시작 기록이다. 다음 세션에서 위 목록을 전부 자동 착수하지 않고 새 요청에 맞춰 범위를 잡는다.

## 8. 기록의 출처와 갱신

사용자가 제시한 [ai-burn-rate PR #6](https://github.com/99jinwoo/ai-burn-rate/pull/6)의 루트 안내 파일·문서 목록 방식을 참고했다. 그 프로젝트의 빌드 도구·버전·서명·허브 운영 규칙은 Twilight의 사실이 아니므로 가져오지 않았다. 원본 [HANDOVER](HANDOVER.md)는 설계 출처로 보존한다.

작업 종료 시 STATUS에 검증/미검증을, DECISIONS에 새 결정을, 이 문서에 기준 버전·배포·후속 작업을 갱신한다. PR·브랜치·로컬 변경 상태는 매번 실제로 확인한다. 문서 PR이 아직 열려 있으면 main에 기록이 들어갔다고 말하지 않는다.
