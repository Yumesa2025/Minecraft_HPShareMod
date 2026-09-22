import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { PERKS_JSON, REPO_ROOT, SETS_JSON, findRepoRoot } from '../src/lib/paths';

describe('정의 파일 경로', () => {
  it('두 정의 파일이 실제로 있다', () => {
    expect(existsSync(PERKS_JSON)).toBe(true);
    expect(existsSync(SETS_JSON)).toBe(true);
  });

  it('저장소 뿌리에는 모드의 settings.gradle 이 있다', () => {
    expect(existsSync(resolve(REPO_ROOT, 'settings.gradle'))).toBe(true);
  });

  it('site/ 에서 찾아도 저장소 뿌리에서 찾아도 같은 곳이 나온다', () => {
    expect(findRepoRoot(resolve(REPO_ROOT, 'site'))).toBe(REPO_ROOT);
    expect(findRepoRoot(REPO_ROOT)).toBe(REPO_ROOT);
  });

  it('깊은 하위 폴더에서 찾아도 같은 곳이 나온다', () => {
    expect(findRepoRoot(resolve(REPO_ROOT, 'site/src/lib'))).toBe(REPO_ROOT);
  });

  it('정의 파일이 없는 곳에서는 조용히 틀린 경로를 내지 않고 터진다', () => {
    expect(() => findRepoRoot(tmpdir())).toThrow(/저장소 뿌리를 찾지 못했습니다/);
  });
});
