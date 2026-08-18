import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, RefreshCw } from '../icons.js';
import DataSourceBadges from '../components/DataSourceBadges.jsx';
// import FinancialMetrics from '../components/FinancialMetrics.jsx';
// import MarketEventsPanel from '../components/MarketEventsPanel.jsx';
import PatternAnalysisCard from '../components/PatternAnalysisCard.jsx';
import PriceChart from '../components/PriceChart.jsx';
import { stocksApi } from '../api/stocksApi.js';

const currencyFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
});

const ranges = ['1M', '3M', '6M', '1Y'];
const LIVE_REFRESH_MS = 7000;

function StockDetailPage() {
  const { symbol } = useParams();
  const [stock, setStock] = useState(null);
  const [prices, setPrices] = useState([]);
  // const [financials, setFinancials] = useState(null);
  const [patternAnalysis, setPatternAnalysis] = useState(null);
  // const [events, setEvents] = useState([]);
  const [range, setRange] = useState('3M');
  const [loading, setLoading] = useState(true);
  const [patternLoading, setPatternLoading] = useState(false);

  const isPositive = useMemo(() => Number(stock?.changeRate ?? 0) >= 0, [stock]);
  const hasPrice = stock?.currentPrice !== null && stock?.currentPrice !== undefined && Number(stock.currentPrice) > 0;
  const hasChangeRate = stock?.changeRate !== null && stock?.changeRate !== undefined;

  useEffect(() => {
    const loadStaticData = async () => {
      setLoading(true);
      try {
        const [stockData, patternData] = await Promise.all([
          stocksApi.getStock(symbol),
          // stocksApi.getFinancials(symbol),
          stocksApi.getLatestPatternAnalysis(symbol),
        ]);
        setStock(stockData);
        // setFinancials(financialData);
        setPatternAnalysis(patternData);
      } finally {
        setLoading(false);
      }
    };

    loadStaticData();
  }, [symbol]);

  // 재무지표/시장 일정은 1차 배포 이후 외부 API 품질을 확정한 뒤 다시 노출합니다.
  // useEffect(() => {
  //   let cancelled = false;
  //
  //   const loadEvents = async () => {
  //     try {
  //       const eventData = await stocksApi.getStockEvents(symbol);
  //       if (!cancelled) {
  //         setEvents(eventData);
  //       }
  //     } catch (error) {
  //       console.error('Failed to load market events.', error);
  //     }
  //   };
  //
  //   loadEvents();
  //
  //   return () => {
  //     cancelled = true;
  //   };
  // }, [symbol]);

  useEffect(() => {
    let cancelled = false;

    const loadLiveData = async () => {
      try {
        const [stockData, priceData] = await Promise.all([
          stocksApi.getStock(symbol),
          stocksApi.getPrices(symbol, range),
        ]);

        if (!cancelled) {
          setStock(stockData);
          setPrices(priceData);
        }
      } catch (error) {
        console.error('Failed to refresh live stock data.', error);
      }
    };

    loadLiveData();
    const intervalId = window.setInterval(loadLiveData, LIVE_REFRESH_MS);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [symbol, range]);

  const refreshPatternAnalysis = async () => {
    setPatternLoading(true);
    try {
      const data = await stocksApi.runPatternAnalysis(symbol);
      setPatternAnalysis(data);
    } finally {
      setPatternLoading(false);
    }
  };

  if (loading || !stock) {
    return <div className="loading-panel">불러오는 중</div>;
  }

  return (
    <div className="detail-page">
      <Link to="/stocks" className="back-link">
        <ArrowLeft size={18} aria-hidden="true" />
        목록
      </Link>

      <section className="stock-hero">
        <div>
          <span className="eyebrow">
            {stock.market} · {stock.sector}
          </span>
          <h1>{stock.name}</h1>
          <p>{stock.description}</p>
        </div>
        <div className="quote-panel">
          <span>{stock.symbol}</span>
          <strong>{hasPrice ? currencyFormatter.format(stock.currentPrice) : '가격 확인 중'}</strong>
          {hasChangeRate && <em className={isPositive ? 'positive' : 'negative'}>{stock.changeRate}%</em>}
        </div>
      </section>

      <DataSourceBadges />

      <section className="detail-section">
        <div className="section-head">
          <div>
            <span className="eyebrow">가격 차트</span>
            <h2>가격 흐름</h2>
          </div>
          <div className="range-control" aria-label="차트 기간">
            {ranges.map((item) => (
              <button
                key={item}
                type="button"
                className={item === range ? 'selected' : undefined}
                onClick={() => setRange(item)}
              >
                {item}
              </button>
            ))}
          </div>
        </div>
        <PriceChart data={prices} />
      </section>

      <section className="detail-section">
        <div className="section-head">
          <div>
            <span className="eyebrow">차트 패턴</span>
            <h2>AI 패턴 분석과 과거 통계</h2>
          </div>
          <button type="button" className="ghost-button" onClick={refreshPatternAnalysis} disabled={patternLoading}>
            <RefreshCw size={18} aria-hidden="true" />
            갱신
          </button>
        </div>
        <PatternAnalysisCard
          analysis={patternAnalysis}
          loading={patternLoading}
          onRefresh={refreshPatternAnalysis}
        />
      </section>

      {/* 재무지표는 OpenDART/시장지표 API 연결이 안정화된 뒤 2차 배포에서 다시 노출합니다.
      <section className="detail-section">
        <div className="section-head">
          <div>
            <span className="eyebrow">기업 지표</span>
            <h2>기업 지표</h2>
          </div>
        </div>
        <FinancialMetrics financials={financials} />
      </section>
      */}

      {/* 시장 일정/뉴스성 이벤트는 외부 일정 API를 확정한 뒤 2차 배포에서 다시 노출합니다.
      <MarketEventsPanel events={events} compact title={`${stock.name}에 영향 줄 수 있는 일정`} />
      */}
    </div>
  );
}

export default StockDetailPage;
