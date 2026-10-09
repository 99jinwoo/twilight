# Twilight and You — Claude Code 작업 안내

이 저장소는 `99jinwoo/twilight`다. 새 세션에서 대화 기록 대신 아래 문서를 읽고 작업한다.

1. [AGENTS.md](AGENTS.md): 공통 작업 규칙·원격 확인·검사·PR·배포 절차. 이 파일에 중복 규칙을 만들지 않는다.
2. [docs/RESUME.md](docs/RESUME.md): 마지막 인수인계, 기준 버전·서명·검증, 남은 과업과 코드 위치.
3. [docs/STATUS.md](docs/STATUS.md), [docs/SPEC.md](docs/SPEC.md), [docs/DECISIONS.md](docs/DECISIONS.md): 구현 상태·요구사항·결정 이력.

작업을 시작하기 전에 원격을 먼저 받아 최신 main·열린 PR·릴리스를 확인한다. 새 과업은 최신 main에서 `feat/*` 브랜치를 만들고 PR로 제공한다. 사용자가 승인한 구현 착수를 다시 묻지 않으며, PR 머지는 사용자가 결정한다.

문서 구성은 사용자가 제시한 ai-burn-rate의 인수인계 방식을 참고했다. 그 프로젝트의 버전 번호·빌드 명령·서명키·서버 설정을 Twilight에 적용하지 않는다. Twilight의 빌드 명령과 배포 제한은 아래 인수인계 문서를 따른다.
