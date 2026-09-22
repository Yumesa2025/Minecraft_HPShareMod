export interface GuideTab {
  id: string;
  name: string;
}

/** 가이드 가로 탭. 차례가 곧 화면에 나오는 차례다. */
export const GUIDE_TABS: GuideTab[] = [
  { id: 'install', name: '설치' },
  { id: 'start', name: '게임 시작' },
  { id: 'perks', name: '증강' },
  { id: 'sets', name: '세트' },
  { id: 'economy', name: '경제 개편' },
  { id: 'commands', name: '명령어' },
];
