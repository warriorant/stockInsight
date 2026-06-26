import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Brain, LineChart, ListFilter } from '../icons.js';
import StockSearchBar from '../components/StockSearchBar.jsx';
import StockCard from '../components/StockCard.jsx';
import MarketEventsPanel from '../components/MarketEventsPanel.jsx';
import { stocksApi } from '../api/stocksApi.js';

const LIVE_REFRESH_MS = 7000;

function HomePage() {
  const [stocks, setStocks] = useState([]);
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    const loadStocks = async (showLoading = false) => {
      if (showLoading) {
        setLoading(true);
      }

      try {
        const [data, eventData] = await Promise.all([
          stocksApi.getStocks(),
          stocksApi.getMarketEvents(),
        ]);
        if (!cancelled) {
          setStocks(data.slice(0, 3));
          setEvents(eventData.slice(0, 3));
        }
      } catch (error) {
        console.error('Failed to refresh live stocks.', error);
      } finally {
        if (!cancelled && showLoading) {
          setLoading(false);
        }
      }
    };

    loadStocks(true);
    const intervalId = window.setInterval(() => loadStocks(false), LIVE_REFRESH_MS);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, []);

  return (
    <div className="home-page">
      <section className="home-search-band">
        <div className="home-copy">
          <span className="eyebrow">
            <Brain size={16} aria-hidden="true" />
            주린이용 실시간 분석
          </span>
          <h1>어려운 숫자를 쉬운 투자 언어로 바꿔서 봅니다.</h1>
          <p>실시간 가격, 재무지표 해석, 시장 이벤트를 한 화면에서 연결해 보여줍니다.</p>
        </div>
        <StockSearchBar />
      </section>

      <section className="quick-links" aria-label="주요 기능">
        <Link to="/stocks">
          <ListFilter size={22} aria-hidden="true" />
          <span>종목 목록</span>
          <ArrowRight size={18} aria-hidden="true" />
        </Link>
        <Link to="/stocks/SAMSUNG">
          <LineChart size={22} aria-hidden="true" />
          <span>삼성전자</span>
          <ArrowRight size={18} aria-hidden="true" />
        </Link>
      </section>

      <section className="section-head">
        <div>
          <span className="eyebrow">관심 종목</span>
          <h2>오늘의 주요 종목</h2>
        </div>
        <Link to="/stocks" className="text-link">
          전체 보기
          <ArrowRight size={17} aria-hidden="true" />
        </Link>
      </section>

      {loading ? (
        <div className="loading-panel">불러오는 중</div>
      ) : (
        <div className="stock-grid">
          {stocks.map((stock) => (
            <StockCard stock={stock} key={stock.symbol} />
          ))}
        </div>
      )}

      {!loading && <MarketEventsPanel events={events} title="이번 주 시장 체크포인트" />}
    </div>
  );
}

export default HomePage;
