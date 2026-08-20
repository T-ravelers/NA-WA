export interface ExploreRegion2Option {
  apiValue: string
  labelKey: string
}

export const SEOUL_REGION1 = '서울'

/** operational_v9에서 region1이 서울인 Event와 Place의 region2 합집합이다. */
export const SEOUL_REGION2_OPTIONS: ExploreRegion2Option[] = [
  { apiValue: '가락·문정', labelKey: 'explore.areas.garakMunjeong' },
  { apiValue: '강남', labelKey: 'explore.areas.gangnam' },
  { apiValue: '건대', labelKey: 'explore.areas.kondae' },
  { apiValue: '공덕', labelKey: 'explore.areas.gongdeok' },
  { apiValue: '공릉', labelKey: 'explore.areas.gongneung' },
  { apiValue: '구로·가산', labelKey: 'explore.areas.guroGasan' },
  { apiValue: '노량진', labelKey: 'explore.areas.noryangjin' },
  { apiValue: '노원', labelKey: 'explore.areas.nowon' },
  { apiValue: '대학로', labelKey: 'explore.areas.daehangno' },
  { apiValue: '동대문·DDP', labelKey: 'explore.areas.dongdaemun' },
  { apiValue: '마곡·김포공항', labelKey: 'explore.areas.magogGimpoAirport' },
  { apiValue: '망원', labelKey: 'explore.areas.mangwon' },
  { apiValue: '명동', labelKey: 'explore.areas.myeongdong' },
  { apiValue: '목동', labelKey: 'explore.areas.mokdong' },
  { apiValue: '반포', labelKey: 'explore.areas.banpo' },
  { apiValue: '봉천·서울대', labelKey: 'explore.areas.bongcheonSnu' },
  { apiValue: '부암·평창', labelKey: 'explore.areas.buamPyeongchang' },
  { apiValue: '삼성·코엑스', labelKey: 'explore.areas.samSeongCoex' },
  { apiValue: '상암', labelKey: 'explore.areas.sangam' },
  { apiValue: '서초', labelKey: 'explore.areas.seocho' },
  { apiValue: '서촌', labelKey: 'explore.areas.seochon' },
  { apiValue: '성북·한성대', labelKey: 'explore.areas.seongbukHansung' },
  { apiValue: '성수', labelKey: 'explore.areas.seongsu' },
  { apiValue: '송리단길', labelKey: 'explore.areas.songridanGil' },
  { apiValue: '수유·미아', labelKey: 'explore.areas.suyuMia' },
  { apiValue: '신림', labelKey: 'explore.areas.sillim' },
  { apiValue: '신촌', labelKey: 'explore.areas.sinchon' },
  { apiValue: '압구정·도산', labelKey: 'explore.areas.apgujeongDosan' },
  { apiValue: '여의도', labelKey: 'explore.areas.yeouido' },
  { apiValue: '연남', labelKey: 'explore.areas.yeonnam' },
  { apiValue: '연희', labelKey: 'explore.areas.yeonhui' },
  { apiValue: '용산', labelKey: 'explore.areas.yongsan' },
  { apiValue: '인사동·북촌', labelKey: 'explore.areas.insadongBukchon' },
  { apiValue: '잠실', labelKey: 'explore.areas.jamsil' },
  { apiValue: '창동·도봉', labelKey: 'explore.areas.changdongDobong' },
  { apiValue: '천호·강동', labelKey: 'explore.areas.cheonghoGangdong' },
  { apiValue: '청담', labelKey: 'explore.areas.cheongdam' },
  { apiValue: '청량리·회기', labelKey: 'explore.areas.cheongnyangniHoegi' },
  { apiValue: '합정', labelKey: 'explore.areas.hapjeong' },
  { apiValue: '한남·이태원', labelKey: 'explore.areas.hannamItaewon' },
  { apiValue: '홍대', labelKey: 'explore.areas.hongdae' },
]

export const VALID_SEOUL_REGION2_VALUES = new Set(
  SEOUL_REGION2_OPTIONS.map((option) => option.apiValue),
)

export function findExploreRegionLabelKey(value: string | null | undefined): string | undefined {
  if (!value) return undefined
  if (value === SEOUL_REGION1) return 'explore.regions.seoul'
  return SEOUL_REGION2_OPTIONS.find((option) => option.apiValue === value)?.labelKey
}
