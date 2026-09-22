import { describe, expect, it } from 'vitest';
import { joinBase } from '../src/lib/url';

describe('joinBase', () => {
  it('base 를 앞에 붙인다', () => {
    expect(joinBase('/sharedfate', '/perks')).toBe('/sharedfate/perks');
  });

  it('base 뒤에 슬래시가 있어도 겹치지 않는다', () => {
    expect(joinBase('/sharedfate/', '/perks')).toBe('/sharedfate/perks');
  });

  it('path 앞에 슬래시가 없어도 된다', () => {
    expect(joinBase('/sharedfate', 'perks')).toBe('/sharedfate/perks');
  });

  it('base 가 뿌리면 그대로다', () => {
    expect(joinBase('/', '/perks')).toBe('/perks');
  });

  it('뿌리 자신은 base 로 간다', () => {
    expect(joinBase('/sharedfate', '/')).toBe('/sharedfate/');
  });

  it('바깥 주소는 건드리지 않는다', () => {
    expect(joinBase('/sharedfate', 'https://github.com/a/b')).toBe('https://github.com/a/b');
  });
});
