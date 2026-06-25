import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Brain, LineChart, ListFilter } from '../icons.js';
import StockSearchBar from '../components/StockSearchBar.jsx';
import StockCard from '../components/StockCard.jsx';
import { stocksApi } from '../api/stocksApi.js';

function HomePage() {
  const [stocks, setStocks] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadStocks = async () => {
      try {
        const data = await stocksApi.getStocks();
        setStocks(data.slice(0, 3));
      } finally {
        setLoading(false);
      }
    };

    loadStocks();
  }, []);

  return (
    <div className="home-page">
      <section className="home-search-band">
        <div className="home-copy">
          <span className="eyebrow">
            <Brain size={16} aria-hidden="true" />
            Mock AI 분석
          </span>
          <h1>종목을 고르면 가격, 지표, 분석을 한 화면에서 봅니다.</h1>
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
          <h2>오늘의 mock 종목</h2>
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
    </div>
  );
}

export default HomePage;
