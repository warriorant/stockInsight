import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import StockCard from '../components/StockCard.jsx';
import StockSearchBar from '../components/StockSearchBar.jsx';
import { stocksApi } from '../api/stocksApi.js';

function StockListPage() {
  const [searchParams] = useSearchParams();
  const keyword = useMemo(() => searchParams.get('keyword') ?? '', [searchParams]);
  const [stocks, setStocks] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadStocks = async () => {
      setLoading(true);
      try {
        const data = keyword ? await stocksApi.searchStocks(keyword) : await stocksApi.getStocks();
        setStocks(data);
      } finally {
        setLoading(false);
      }
    };

    loadStocks();
  }, [keyword]);

  return (
    <div className="list-page">
      <section className="page-title-row">
        <div>
          <span className="eyebrow">종목 탐색</span>
          <h1>{keyword ? `"${keyword}" 검색 결과` : '종목 목록'}</h1>
        </div>
        <StockSearchBar compact />
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

      {!loading && stocks.length === 0 && <div className="empty-panel">일치하는 종목이 없습니다.</div>}
    </div>
  );
}

export default StockListPage;
