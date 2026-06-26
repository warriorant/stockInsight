import { BookOpen, CircleHelp } from '../icons.js';

const numberFormatter = new Intl.NumberFormat('ko-KR', {
  maximumFractionDigits: 1,
});

const integerFormatter = new Intl.NumberFormat('ko-KR', {
  maximumFractionDigits: 0,
});

function formatMarketCap(value) {
  if (!value) {
    return '-';
  }
  return `${integerFormatter.format(Number(value) / 1000000000000)}조원`;
}

function metricTone(type, value) {
  const numericValue = Number(value);

  if (!Number.isFinite(numericValue)) {
    return 'neutral';
  }

  if (type === 'per') {
    if (numericValue < 12) return 'good';
    if (numericValue > 28) return 'watch';
  }

  if (type === 'pbr') {
    if (numericValue < 1) return 'good';
    if (numericValue > 3) return 'watch';
  }

  if (type === 'roe') {
    if (numericValue >= 12) return 'good';
    if (numericValue < 6) return 'watch';
  }

  if (type === 'debtRatio') {
    if (numericValue < 80) return 'good';
    if (numericValue > 120) return 'watch';
  }

  if (type === 'revenueGrowth') {
    if (numericValue >= 10) return 'good';
    if (numericValue < 3) return 'watch';
  }

  return 'neutral';
}

function metricInterpretation(type, value) {
  if (type === 'marketCap') {
    return '시장에서 보는 회사 규모를 한눈에 보는 숫자예요.';
  }

  const numericValue = Number(value);

  if (!Number.isFinite(numericValue)) {
    return '데이터를 확인하기 어렵습니다.';
  }

  if (type === 'per') {
    if (numericValue < 12) return '이익 대비 가격 부담이 낮은 편이에요.';
    if (numericValue > 28) return '미래 성장 기대가 많이 반영됐을 수 있어요.';
    return '이익 대비 가격 부담은 중간 정도예요.';
  }

  if (type === 'pbr') {
    if (numericValue < 1) return '장부상 자산보다 낮게 거래되는 구간이에요.';
    if (numericValue > 3) return '자산 가치보다 성장 기대를 더 크게 보는 구간이에요.';
    return '자산 가치 대비 가격은 무난한 편이에요.';
  }

  if (type === 'roe') {
    if (numericValue >= 12) return '자본으로 이익을 만드는 힘이 좋은 편이에요.';
    if (numericValue < 6) return '수익성이 약한 편이라 개선 여부를 봐야 해요.';
    return '수익성은 보통 수준이에요.';
  }

  if (type === 'eps') {
    return '주식 한 주가 벌어들이는 이익을 뜻해요.';
  }

  if (type === 'revenueGrowth') {
    if (numericValue >= 10) return '매출 성장 속도가 꽤 좋은 편이에요.';
    if (numericValue < 3) return '성장 속도가 둔화됐을 수 있어요.';
    return '매출은 완만하게 성장하고 있어요.';
  }

  if (type === 'dividendYield') {
    if (numericValue <= 0) return '배당보다 성장 기대를 더 봐야 하는 종목이에요.';
    return '주가 대비 배당으로 받을 수 있는 비율이에요.';
  }

  if (type === 'debtRatio') {
    if (numericValue < 80) return '부채 부담은 비교적 안정적인 편이에요.';
    if (numericValue > 120) return '부채 부담이 커질 수 있어 주의가 필요해요.';
    return '부채 부담은 보통 수준이에요.';
  }

  return '기업 규모를 가늠하는 기본 지표예요.';
}

function FinancialMetrics({ financials }) {
  if (!financials) {
    return <div className="empty-panel">재무 데이터가 없습니다.</div>;
  }

  const metrics = [
    {
      key: 'marketCap',
      label: '시가총액',
      value: formatMarketCap(financials.marketCap),
      plainLabel: '회사 전체 가격',
      help: '주식시장에서 평가받는 회사 전체 몸값입니다.',
      caution: '규모가 크다고 항상 안전한 것은 아니고, 성장 속도도 같이 봐야 해요.',
      rawValue: financials.marketCap,
    },
    {
      key: 'per',
      label: 'PER',
      value: `${numberFormatter.format(financials.per)}배`,
      plainLabel: '이익 대비 가격',
      help: '회사가 버는 돈에 비해 주가가 몇 배인지 보는 지표입니다.',
      caution: '낮다고 무조건 싸고, 높다고 무조건 비싼 것은 아니에요.',
      rawValue: financials.per,
    },
    {
      key: 'pbr',
      label: 'PBR',
      value: `${numberFormatter.format(financials.pbr)}배`,
      plainLabel: '자산 대비 가격',
      help: '회사가 가진 순자산에 비해 주가가 어느 정도인지 보는 지표입니다.',
      caution: '플랫폼이나 성장주는 자산보다 기대가 더 크게 반영될 수 있어요.',
      rawValue: financials.pbr,
    },
    {
      key: 'roe',
      label: 'ROE',
      value: `${numberFormatter.format(financials.roe)}%`,
      plainLabel: '돈 버는 효율',
      help: '회사가 자기자본으로 이익을 얼마나 잘 만드는지 보여줍니다.',
      caution: '부채를 많이 써서 ROE가 높아지는 경우도 있어요.',
      rawValue: financials.roe,
    },
    {
      key: 'eps',
      label: 'EPS',
      value: `${integerFormatter.format(financials.eps)}원`,
      plainLabel: '한 주당 이익',
      help: '주식 1주가 벌어들인 이익입니다.',
      caution: '일회성 이익이 섞였는지 함께 확인하면 좋아요.',
      rawValue: financials.eps,
    },
    {
      key: 'revenueGrowth',
      label: '매출 성장',
      value: `${numberFormatter.format(financials.revenueGrowth)}%`,
      plainLabel: '매출 증가 속도',
      help: '회사의 판매 규모가 전보다 얼마나 커졌는지 보여줍니다.',
      caution: '매출이 늘어도 이익이 같이 늘지 않으면 부담이 될 수 있어요.',
      rawValue: financials.revenueGrowth,
    },
    {
      key: 'dividendYield',
      label: '배당 수익률',
      value: `${numberFormatter.format(financials.dividendYield)}%`,
      plainLabel: '배당 매력',
      help: '현재 주가 대비 배당금 비율입니다.',
      caution: '배당이 높아도 주가가 크게 빠지면 총수익은 낮아질 수 있어요.',
      rawValue: financials.dividendYield,
    },
    {
      key: 'debtRatio',
      label: '부채비율',
      value: `${numberFormatter.format(financials.debtRatio)}%`,
      plainLabel: '빚 부담',
      help: '회사가 가진 자본 대비 빚이 어느 정도인지 보여줍니다.',
      caution: '업종마다 적정 부채 수준이 다르기 때문에 업종 비교가 필요해요.',
      rawValue: financials.debtRatio,
    },
  ];

  return (
    <section className="beginner-metrics">
      <div className="metric-guide">
        <BookOpen size={20} aria-hidden="true" />
        <div>
          <strong>숫자를 외우기보다 의미를 먼저 보세요.</strong>
          <p>각 지표는 “싸다/비싸다”를 단정하는 답이 아니라, 기업을 이해하는 질문에 가깝습니다.</p>
        </div>
      </div>

      <div className="metrics-grid">
        {metrics.map((metric) => (
          <article className={`metric-cell ${metricTone(metric.key, metric.rawValue)}`} key={metric.label}>
            <div className="metric-cell-head">
              <span>{metric.label}</span>
              <CircleHelp size={17} aria-hidden="true" />
            </div>
            <strong>{metric.value}</strong>
            <em>{metric.plainLabel}</em>
            <p>{metric.help}</p>
            <div className="metric-read">
              <b>쉽게 말하면</b>
              <span>{metricInterpretation(metric.key, metric.rawValue)}</span>
            </div>
            <small>{metric.caution}</small>
          </article>
        ))}
      </div>
    </section>
  );
}

export default FinancialMetrics;
