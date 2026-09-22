import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

/** 저장소 뿌리를 알아보는 표식이자, 우리가 실제로 읽어야 하는 파일. */
const PERKS_REL = 'src/main/resources/sharedfate-perks-default.json';
const SETS_REL = 'src/main/resources/sharedfate-sets-default.json';

/**
 * 정의 파일이 보일 때까지 위로 올라가며 저장소 뿌리를 찾는다.
 *
 * `import.meta.url` 로 이 파일의 위치를 재면 안 된다 — Astro 가 프리렌더할 때 이 모듈을
 * `dist/.prerender/chunks/` 로 번들링해서, 그 시점의 경로가 원본(`site/src/lib`)과 다르다.
 *
 * `process.cwd()` 하나만 믿어도 안 된다 — `site/` 에서 돌리면 맞지만 저장소 뿌리에서
 * `npm --prefix site run build` 로 돌리면 어긋난다. 그래서 어느 자리에서 시작하든
 * 표식이 보일 때까지 거슬러 올라간다.
 *
 * 못 찾으면 **조용히 틀린 경로를 내지 않고 터진다.** 경로가 어긋난 채로 빌드가 통과하면
 * 증강이 0개인 사이트가 배포된다.
 */
export function findRepoRoot(start: string): string {
  let dir = resolve(start);
  for (;;) {
    if (existsSync(resolve(dir, PERKS_REL))) {
      return dir;
    }
    const parent = dirname(dir);
    if (parent === dir) {
      throw new Error(
        `SharedFate 저장소 뿌리를 찾지 못했습니다. ${resolve(start)} 에서 위로 올라가며 ${PERKS_REL} 을 찾았습니다.`,
      );
    }
    dir = parent;
  }
}

/** 모드 저장소의 뿌리. */
export const REPO_ROOT = findRepoRoot(process.cwd());

/** 증강 정의. 사본을 만들지 않고 원본을 읽는다. */
export const PERKS_JSON = resolve(REPO_ROOT, PERKS_REL);

/** 세트 정의. */
export const SETS_JSON = resolve(REPO_ROOT, SETS_REL);
