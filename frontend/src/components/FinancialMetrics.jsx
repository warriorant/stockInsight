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

function FinancialMetrics({ financials }) {
  if (!financials) {
    return <div className="empty-panel">재무 데이터가 없습니다.</div>;
  }

  const metrics = [
    { label: '시가총액', value: formatMarketCap(financials.marketCap) },
    { label: 'PER', value: `${numberFormatter.format(financials.per)}배` },
    { label: 'PBR', value: `${numberFormatter.format(financials.pbr)}배` },
    { label: 'ROE', value: `${numberFormatter.format(financials.roe)}%` },
    { label: 'EPS', value: `${integerFormatter.format(financials.eps)}원` },
    { label: '매출 성장', value: `${numberFormatter.format(financials.revenueGrowth)}%` },
    { label: '배당 수익률', value: `${numberFormatter.format(financials.dividendYield)}%` },
    { label: '부채비율', value: `${numberFormatter.format(financials.debtRatio)}%` },
  ];

  return (
    <div className="metrics-grid">
      {metrics.map((metric) => (
        <div className="metric-cell" key={metric.label}>
          <span>{metric.label}</span>
          <strong>{metric.value}</strong>
        </div>
      ))}
    </div>
  );
}

export default FinancialMetrics;

