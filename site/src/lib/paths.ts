import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// 이 파일은 site/src/lib 에 있다. 세 번 올라가면 저장소 뿌리다.
const HERE = dirname(fileURLToPath(import.meta.url));

/** 모드 저장소의 뿌리. */
export const REPO_ROOT = resolve(HERE, '../../..');

/** 증강 정의. 사본을 만들지 않고 원본을 읽는다. */
export const PERKS_JSON = resolve(REPO_ROOT, 'src/main/resources/sharedfate-perks-default.json');

/** 세트 정의. */
export const SETS_JSON = resolve(REPO_ROOT, 'src/main/resources/sharedfate-sets-default.json');
