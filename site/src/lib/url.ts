/** base 와 경로를 슬래시 겹침 없이 잇는다. http 로 시작하면 그대로 둔다. */
export function joinBase(base: string, path: string): string {
  if (/^https?:\/\//.test(path)) return path;
  const left = base.replace(/\/+$/, '');
  const right = path.replace(/^\/+/, '');
  if (right === '') return `${left}/`;
  return `${left}/${right}`;
}

/** 페이지 안에서 쓰는 것. astro.config.mjs 의 base 를 자동으로 붙인다. */
export function href(path: string): string {
  return joinBase(import.meta.env.BASE_URL ?? '/', path);
}
