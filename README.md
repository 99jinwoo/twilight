# Twilight and You

한국의 날씨·미세먼지·박명을 홈화면에서 함께 보는 개인용 Android 위젯 앱.

현재는 **0.3.2-preview 측정소 자동 연결·가로 위젯 개선판**이다. 박명은 좌표만으로 기기에서 계산한다. 기상청·에어코리아 연결 기능은 앱에 인증키를 입력해 사용하며 사용자가 기상청·먼지 실측 수신을 확인했다. 먼지 예보와 현재 위치 확인도 사용자 화면에서 확인했다. 키가 없으면 날씨·먼지는 빈 값이고 박명은 표시한다. 샘플은 별도로 켠 경우에만 사용한다.

[최신 설치 파일](https://github.com/99jinwoo/twilight/releases)에서 APK를 다운로드한다. 테스트 서명이 달라 업데이트 설치가 거부되면 기존 미리보기 앱을 삭제하고 새로 설치해야 한다. 삭제하면 위젯과 설정을 다시 구성해야 한다.

## 사용 흐름

앱에서 1×1·가로 2×1·세로 1×2 배치와 스타일을 살펴보고 홈화면에 추가한다. 위젯을 누르면 그 위젯의 설정을 편집한다. 고정 지역을 선택하거나 ‘지역과 위치’에서 앱 실행·새로고침 때 현재 위치를 쓰도록 설정한다. ‘실제 자료 → API 연결’에서 키를 저장하면 가까운 측정소를 자동 선택해 조회한다. 직접 지정은 선택 사항이다. 좁은 가로 위젯에서도 네 시간 예보를 가로 네 칸으로 유지한다. [키 신청·설정 안내](docs/API_SETUP.md)를 참고한다.

## 개발 환경

JDK 17, Android SDK Platform 36, Build Tools 35.0.0. Android Studio에서 저장소를 열거나 로컬 `ANDROID_HOME`/`local.properties`에 SDK 경로를 지정한다.

```sh
./gradlew lint test assembleDebug
./gradlew connectedDebugAndroidTest
```

두 번째 명령은 Android 10 이상 에뮬레이터나 연결된 기기가 필요하다. 디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성된다. 빌드에 날씨 API 키가 필요하지 않다.

GitHub Actions는 PR마다 빌드 및 API 29/35 검사를 실행하도록 구성되어 있다. 실제 실행 결과와 남은 검증은 [STATUS](docs/STATUS.md)에서 확인한다.

## 새 창에서 작업 재개

먼저 [공통 작업 규칙](AGENTS.md)과 [재시작 안내](docs/RESUME.md)를 읽는다. Claude Code용 [CLAUDE.md](CLAUDE.md)도 같은 문서로 연결된다. 이전 대화 없이 기준 버전·서명 상태·검증·남은 과업을 확인할 수 있다.

현재 배포 기준은 v0.3.2-preview(versionCode 5), PR #2까지 main에 병합됐다. 새 작업은 원격 main·열린 PR·릴리스를 먼저 확인하고 새 브랜치에서 진행한다. 고정 배포 서명과 실기기 후속 검증은 아직 남아 있다.

## 문서

- [공통 작업 규칙](AGENTS.md) · [Claude Code 진입점](CLAUDE.md)
- [새 세션 재시작·마지막 인수인계](docs/RESUME.md)
- [API 연결 안내](docs/API_SETUP.md)
- [박명 검증 결과](docs/ASTRONOMY_VALIDATION.md)
- [현재 사양](docs/SPEC.md)
- [결정 이력](docs/DECISIONS.md)
- [구현·검증 상태](docs/STATUS.md)
- [원본 인수인계](docs/HANDOVER.md)

Android 10(minSdk 29)을 하한으로 잡았다. Note9은 Android 10 업데이트 기기를 기준으로 하며, 사용자가 v0.1.0-preview의 Note9 정상 동작을 확인했다. 새 버전의 실기기 가독성과 장시간 동작은 별도 확인이 필요하다.
