import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { PERKS_JSON } from '../src/lib/paths';
import { loadPerks } from '../src/lib/perks';

const raw = JSON.parse(readFileSync(PERKS_JSON, 'utf-8'));
const perks = loadPerks();

describe('증강 읽기', () => {
  it('정의 파일과 개수가 같다', () => {
    expect(perks.length).toBe(raw.perks.length);
  });

  it('모든 증강에 id·이름·설명이 있다', () => {
    for (const perk of perks) {
      expect(perk.id).toBeTruthy();
      expect(perk.name.length).toBeGreaterThan(0);
      expect(perk.description.length).toBeGreaterThan(0);
    }
  });

  it('등급은 셋뿐이다', () => {
    const rarities = new Set(perks.map((p) => p.rarity));
    expect([...rarities].sort()).toEqual(['gold', 'prism', 'silver']);
  });

  it('세 등급이 모두 하나 이상 있다', () => {
    for (const rarity of ['silver', 'gold', 'prism'] as const) {
      expect(perks.filter((p) => p.rarity === rarity).length).toBeGreaterThan(0);
    }
  });

  it('setTypes 는 언제나 배열이다 — 유형 없는 증강은 빈 배열', () => {
    for (const perk of perks) {
      expect(Array.isArray(perk.setTypes)).toBe(true);
    }
    expect(perks.some((p) => p.setTypes.length === 0)).toBe(true);
  });

  it('등급 차례로 정렬돼 있다 — 실버 → 골드 → 프리즘', () => {
    const order = { silver: 0, gold: 1, prism: 2 } as const;
    const seen = perks.map((p) => order[p.rarity]);
    expect(seen).toEqual([...seen].sort((a, b) => a - b));
  });
});
