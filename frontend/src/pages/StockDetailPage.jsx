import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, RefreshCw } from '../icons.js';
import AiAnalysisCard from '../components/AiAnalysisCard.jsx';
import FinancialMetrics from '../components/FinancialMetrics.jsx';
import PriceChart from '../components/PriceChart.jsx';
import { stocksApi } from '../api/stocksApi.js';

const currencyFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
});

const ranges = ['1M', '3M', '6M', '1Y'];

function StockDetailPage() {
  const { symbol } = useParams();
  const [stock, setStock] = useState(null);
  const [prices, setPrices] = useState([]);
  const [financials, setFinancials] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [range, setRange] = useState('3M');
  const [loading, setLoading] = useState(true);
  const [analysisLoading, setAnalysisLoading] = useState(false);

  const isPositive = useMemo(() => Number(stock?.changeRate ?? 0) >= 0, [stock]);

  useEffect(() => {
    const loadStaticData = async () => {
      setLoading(true);
      try {
        const [stockData, financialData, analysisData] = await Promise.all([
          stocksApi.getStock(symbol),
          stocksApi.getFinancials(symbol),
          stocksApi.getLatestAnalysis(symbol),
        ]);
        setStock(stockData);
        setFinancials(financialData);
        setAnalysis(analysisData);
      } finally {
        setLoading(false);
      }
    };

    loadStaticData();
  }, [symbol]);

  useEffect(() => {
    const loadPrices = async () => {
      const priceData = await stocksApi.getPrices(symbol, range);
      setPrices(priceData);
    };

    loadPrices();
  }, [symbol, range]);

  const refreshAnalysis = async () => {
    setAnalysisLoading(true);
    try {
      const data = await stocksApi.runAnalysis(symbol);
      setAnalysis(data);
    } finally {
      setAnalysisLoading(false);
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
          <strong>{currencyFormatter.format(stock.currentPrice)}</strong>
          <em className={isPositive ? 'positive' : 'negative'}>{stock.changeRate}%</em>
        </div>
      </section>

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
            <span className="eyebrow">기업 지표</span>
            <h2>기업 지표</h2>
          </div>
        </div>
        <FinancialMetrics financials={financials} />
      </section>

      <section className="detail-section">
        <div className="section-head">
          <div>
            <span className="eyebrow">AI 분석</span>
            <h2>AI 분석 결과</h2>
          </div>
          <button type="button" className="ghost-button" onClick={refreshAnalysis} disabled={analysisLoading}>
            <RefreshCw size={18} aria-hidden="true" />
            갱신
          </button>
        </div>
        <AiAnalysisCard analysis={analysis} loading={analysisLoading} onRefresh={refreshAnalysis} />
      </section>
    </div>
  );
}

export default StockDetailPage;
