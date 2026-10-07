# Twilight and You

한국의 날씨·미세먼지·박명을 홈화면에서 함께 보는 개인용 Android 위젯 앱.

현재는 **0.2.0-preview 배치 검증판**이다. 실제 관측·예보와 천문 계산은 아직 연결하지 않았다. 기본 홈 위젯은 미연결 상태이며, 앱에서 명시적으로 샘플 표시를 켤 수 있다.

[최신 설치 파일](https://github.com/99jinwoo/twilight/releases)에서 APK를 다운로드한다. 테스트 서명이 달라 업데이트 설치가 거부되면 기존 미리보기 앱을 삭제하고 새로 설치해야 한다. 삭제하면 위젯과 설정을 다시 구성해야 한다.

## 사용 흐름

앱에서 1×1·가로 2×1·세로 1×2 배치와 스타일을 살펴보고 홈화면에 추가한다. 위젯을 누르면 그 위젯의 설정을 편집한다. 지역 이름은 현재 배치용이며 위치 확인 기능은 후속 단계다.

## 개발 환경

JDK 17, Android SDK Platform 36, Build Tools 35.0.0. Android Studio에서 저장소를 열거나 로컬 `ANDROID_HOME`/`local.properties`에 SDK 경로를 지정한다.

```sh
./gradlew lint test assembleDebug
./gradlew connectedDebugAndroidTest
```

두 번째 명령은 Android 10 이상 에뮬레이터나 연결된 기기가 필요하다. 디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성된다. 빌드에 날씨 API 키가 필요하지 않다.

GitHub Actions는 PR마다 빌드 및 API 29/35 검사를 실행하도록 구성되어 있다. 실제 실행 결과와 남은 검증은 [STATUS](docs/STATUS.md)에서 확인한다.

## 문서

- [현재 사양](docs/SPEC.md)
- [결정 이력](docs/DECISIONS.md)
- [구현·검증 상태](docs/STATUS.md)
- [원본 인수인계](docs/HANDOVER.md)

Android 10(minSdk 29)을 하한으로 잡았다. Note9은 Android 10 업데이트 기기를 기준으로 하며, 사용자가 v0.1.0-preview의 Note9 정상 동작을 확인했다. 새 버전의 실기기 가독성과 장시간 동작은 별도 확인이 필요하다.
