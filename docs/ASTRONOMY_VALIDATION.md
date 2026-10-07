# 박명 계산 검증

2026-10-08 / v0.3.0-preview

시민·항해·천문박명 각각의 아침·저녁 **108개 기준 시각**을 USNO 연간 표와 비교했다. 원시 계산 시각과 분 단위 기준표의 최대 차이는 **30초**이며 목표 120초 이내를 모두 통과했다. 이는 아래 시험 범위의 결과이며 모든 날짜·위치에 대한 오차 보증은 아니다.

| 지역 | 위도 | 경도 | 날짜 |
|---|---:|---:|---|
| 서울 | 37.5665 | 126.9780 | 2026-03-20, 06-21, 09-23, 12-21 |
| 부산 | 35.1796 | 129.0756 | 동일 |
| 제주 | 33.4996 | 126.5312 | 동일 |
| 강릉 | 37.7519 | 128.8761 | 동일 |
| 서울 | 37.5665 | 126.9780 | 2028-02-29, 12-31 |

18개 지역·날짜 × 3종류 × 아침/저녁 = 108개. 기준표 전사는 `app/src/test/resources/astronomy/usno.csv`에 들어 있으며 재현 검사는 `SolarCalculatorTest`다.

## 방법과 가정

[NOAA 계산 설명](https://gml.noaa.gov/grad/solcalc/calcdetails.html)의 Meeus 기반 태양 좌표·균시차 식으로 기하학적 고도를 구한다. 지역 날짜 내 120초 간격으로 목표 고도의 통과 구간을 찾고 1초 이하까지 이분 탐색한다. 상승/하강 사건을 구분한다.

시민 −6°, 항해 −12°, 천문 −18°의 태양 중심 고도를 사용한다. 한국 시간 Asia/Seoul(UTC+9)이며 USNO 표도 +9시간으로 요청했다. [USNO 연간 표](https://aa.usno.navy.mil/data/RS_OneYear)의 task=2/3/4는 각각 시민/항해/천문이다. 산·건물·실제 관측 고도·날씨에 따른 가시성은 반영하지 않는다. 일출몰 −0.8333° 계산은 내부 지원하지만 이번 독립 기준 비교는 세 박명만을 대상으로 한다.

직전 ≤ 현재 < 다음은 반올림 전 사건 시각으로 고른다. 표시는 지역 시간으로 변환한 뒤 가장 가까운 분으로 반올림한다. 따라서 표시된 분의 초반에는 내부 사건 시각까지 잠시 ‘다음’으로 남을 수 있다. 위젯 재표시는 1시간 작업과 Android 실행 시점에 따르며 정확한 사건 순간 자동 전환을 보장하지 않는다.

별도로 극지 사건 없음, 정확한 사건 시각의 전환, 지역·날짜 변화, 윤일·연말·한국 시간대, 공개 좌표의 기상청 격자 변환을 검사한다. 네트워크 없이 계산하며 API 인증키가 필요하지 않다.

## 기준표 주소

- [USNO 표 1](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=2&lat=37.5665&lon=126.978&label=Seoul&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 2](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=3&lat=37.5665&lon=126.978&label=Seoul&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 3](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=4&lat=37.5665&lon=126.978&label=Seoul&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 4](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2028&task=2&lat=37.5665&lon=126.978&label=Seoul&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 5](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2028&task=3&lat=37.5665&lon=126.978&label=Seoul&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 6](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2028&task=4&lat=37.5665&lon=126.978&label=Seoul&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 7](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=2&lat=35.1796&lon=129.0756&label=Busan&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 8](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=3&lat=35.1796&lon=129.0756&label=Busan&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 9](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=4&lat=35.1796&lon=129.0756&label=Busan&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 10](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=2&lat=33.4996&lon=126.5312&label=Jeju&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 11](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=3&lat=33.4996&lon=126.5312&label=Jeju&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 12](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=4&lat=33.4996&lon=126.5312&label=Jeju&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 13](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=2&lat=37.7519&lon=128.8761&label=Gangneung&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 14](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=3&lat=37.7519&lon=128.8761&label=Gangneung&tz=9&tz_sign=1&submit=Get+Data)
- [USNO 표 15](https://aa.usno.navy.mil/calculated/rstt/year?ID=AA&year=2026&task=4&lat=37.7519&lon=128.8761&label=Gangneung&tz=9&tz_sign=1&submit=Get+Data)
