import { Brain, LineChart, ShieldAlert } from '../icons.js';

const sources = [
  {
    key: 'quote',
    icon: LineChart,
    label: '현재가',
    status: '실제 연동',
    source: '네이버 실시간',
    tone: 'live',
    note: 'NXT/통합가가 아닌 임시 현재가',
  },
  {
    key: 'chart',
    icon: LineChart,
    label: '차트',
    status: '실제 연동',
    source: 'Yahoo Finance',
    tone: 'live',
    note: '일봉 가격 데이터 기준',
  },
  // 재무 데이터 출처는 OpenDART/시장지표 API 연결 품질을 확정한 뒤 다시 노출합니다.
  // {
  //   key: 'financials',
  //   icon: CircleHelp,
  //   label: '재무',
  //   status: '실제 연동',
  //   source: 'OpenDART',
  //   tone: 'live',
  //   note: '확인된 항목만 표시',
  // },
  {
    key: 'analysis',
    icon: Brain,
    label: '패턴 AI',
    status: '실제 연동',
    source: 'AI 서버1/2',
    tone: 'live',
    note: 'DB 캔들로 이미지 생성 후 패턴 분류',
  },
  // 일정 데이터 출처는 외부 일정/뉴스 API 연결 뒤 다시 노출합니다.
  // {
  //   key: 'events',
  //   icon: CalendarDays,
  //   label: '일정',
  //   status: '규칙 계산',
  //   source: '만기일 계산',
  //   tone: 'rule',
  //   note: '계산 가능한 일정만 표시',
  // },
];

function DataSourceBadges() {
  return (
    <section className="source-panel" aria-label="데이터 출처">
      <div className="source-panel-head">
        <ShieldAlert size={18} aria-hidden="true" />
        <span>데이터 출처</span>
      </div>
      <div className="source-badges">
        {sources.map((source) => {
          const Icon = source.icon;

          return (
            <span className={`source-badge ${source.tone}`} key={source.key} title={source.note}>
              <Icon size={15} aria-hidden="true" />
              <span>
                <b>{source.label}</b>
                <em>{source.status}</em>
                <small>
                  {source.source} · {source.note}
                </small>
              </span>
            </span>
          );
        })}
      </div>
    </section>
  );
}

export default DataSourceBadges;
