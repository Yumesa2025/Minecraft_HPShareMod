import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { PERKS_JSON, SETS_JSON } from '../src/lib/paths';
import { SET_TYPE_NAMES } from '../src/lib/setTypes';

const sets = JSON.parse(readFileSync(SETS_JSON, 'utf-8'));
const perks = JSON.parse(readFileSync(PERKS_JSON, 'utf-8'));

describe('세트 유형 표', () => {
  it('정의 파일의 모든 유형에 한국어 이름이 있다', () => {
    const missing = sets.sets
      .map((s: { type: string }) => s.type)
      .filter((type: string) => !(type in SET_TYPE_NAMES));
    expect(missing).toEqual([]);
  });

  it('증강이 쓰는 모든 유형에 한국어 이름이 있다', () => {
    const used = new Set<string>();
    for (const perk of perks.perks) {
      for (const type of perk.set_types ?? []) used.add(type);
    }
    const missing = [...used].filter((type) => !(type in SET_TYPE_NAMES));
    expect(missing).toEqual([]);
  });

  it('표에만 있고 정의에는 없는 유형이 없다', () => {
    const defined = new Set(sets.sets.map((s: { type: string }) => s.type));
    const stale = Object.keys(SET_TYPE_NAMES).filter((type) => !defined.has(type));
    expect(stale).toEqual([]);
  });
});
