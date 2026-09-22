import { resolve } from 'node:path';

// import.meta.url 로 이 파일 위치를 찾으면 안 된다 — Astro 빌드가 프리렌더할 때 이
// 모듈을 dist/.prerender/chunks 로 번들링해서, 그 시점엔 청크 파일 위치를 기준으로
// 상대 경로가 어긋난다 (예: site/src/lib 대신 site/dist/.prerender/chunks). 대신
// 항상 site/ 에서 실행된다고 보장되는 process.cwd() 를 쓴다 (astro dev/build, vitest 모두).
const SITE_ROOT = process.cwd();

/** 모드 저장소의 뿌리. */
export const REPO_ROOT = resolve(SITE_ROOT, '..');

/** 증강 정의. 사본을 만들지 않고 원본을 읽는다. */
export const PERKS_JSON = resolve(REPO_ROOT, 'src/main/resources/sharedfate-perks-default.json');

/** 세트 정의. */
export const SETS_JSON = resolve(REPO_ROOT, 'src/main/resources/sharedfate-sets-default.json');
