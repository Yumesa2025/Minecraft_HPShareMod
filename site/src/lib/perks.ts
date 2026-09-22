import { readFileSync } from 'node:fs';
import { PERKS_JSON } from './paths';

export type Rarity = 'silver' | 'gold' | 'prism';

export interface Perk {
  id: string;
  name: string;
  description: string;
  rarity: Rarity;
  /** 유형 id 들. 유형이 없으면 빈 배열이다. 한 증강이 둘을 가질 수 있다. */
  setTypes: string[];
}

interface RawPerk {
  id: string;
  name: string;
  description?: string;
  rarity: Rarity;
  set_types?: string[];
}

const RARITY_ORDER: Record<Rarity, number> = { silver: 0, gold: 1, prism: 2 };

/** 등급의 한국어 이름. PerkRarity.java 의 displayName 과 같다. */
export const RARITY_NAME: Record<Rarity, string> = {
  silver: '실버',
  gold: '골드',
  prism: '프리즘',
};

/** 정의 파일을 읽어 증강 목록을 낸다. 등급 차례, 같은 등급 안에서는 이름 차례다. */
export function loadPerks(): Perk[] {
  const raw = JSON.parse(readFileSync(PERKS_JSON, 'utf-8')) as { perks: RawPerk[] };
  return raw.perks
    .map((perk) => ({
      id: perk.id,
      name: perk.name,
      description: perk.description ?? '',
      rarity: perk.rarity,
      setTypes: perk.set_types ?? [],
    }))
    .sort((a, b) => {
      const byRarity = RARITY_ORDER[a.rarity] - RARITY_ORDER[b.rarity];
      return byRarity !== 0 ? byRarity : a.name.localeCompare(b.name, 'ko');
    });
}

/** 등급별 개수. 화면에 「실버 34 · 골드 35 · 프리즘 25」를 찍을 때 쓴다. */
export function countByRarity(perks: Perk[]): Record<Rarity, number> {
  return {
    silver: perks.filter((p) => p.rarity === 'silver').length,
    gold: perks.filter((p) => p.rarity === 'gold').length,
    prism: perks.filter((p) => p.rarity === 'prism').length,
  };
}
