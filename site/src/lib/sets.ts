import { readFileSync } from 'node:fs';
import { SETS_JSON } from './paths';
import { SET_TYPE_ORDER, setTypeName } from './setTypes';

export interface SetTier {
  /** 이 단계가 열리는 데 필요한 증강 개수. */
  count: number;
  description: string;
}

export interface PerkSet {
  type: string;
  name: string;
  /**
   * 첫 보상이 열리는 개수. 단계 가운데 가장 작은 count 다.
   *
   * PerkSetType.threshold() 와 다른 값이며, 그것을 가져오지 않는다. threshold() 는
   * 「몇 개면 세트라 부를 만한가」를 시뮬레이션으로 정한 설계 지표라 실제로 보상이 열리는
   * 개수와 어긋나는 자리가 있다 (PerkSetRegistry.java:264-268).
   */
  opensAt: number;
  tiers: SetTier[];
}

interface RawSet {
  type: string;
  tiers: { count: number; description?: string }[];
}

/** 정의 파일을 읽어 세트 목록을 낸다. PerkSetType 의 선언 순서를 따른다. */
export function loadSets(): PerkSet[] {
  const raw = JSON.parse(readFileSync(SETS_JSON, 'utf-8')) as { sets: RawSet[] };
  const byType = new Map(raw.sets.map((set) => [set.type, set]));

  return SET_TYPE_ORDER.filter((type) => byType.has(type)).map((type) => {
    const set = byType.get(type)!;
    const tiers = set.tiers
      .map((tier) => ({ count: tier.count, description: tier.description ?? '' }))
      .sort((a, b) => a.count - b.count);
    return {
      type,
      name: setTypeName(type),
      opensAt: Math.min(...tiers.map((t) => t.count)),
      tiers,
    };
  });
}

/** 유형 id 로 바로 찾을 수 있게 한 벌. 유형 이름표 오버레이에서 쓴다. */
export function setsByType(sets: PerkSet[]): Map<string, PerkSet> {
  return new Map(sets.map((set) => [set.type, set]));
}
