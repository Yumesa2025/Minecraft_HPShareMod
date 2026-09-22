import { defineConfig } from 'astro/config';

// 저장소 이름을 sharedfate 로 바꾼 뒤의 주소다. 이름을 안 바꾸면 base 도 함께 고쳐야 한다.
export default defineConfig({
  site: 'https://yumesa2025.github.io',
  base: '/sharedfate',
});
