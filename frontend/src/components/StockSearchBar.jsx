import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, X } from '../icons.js';
import { stocksApi } from '../api/stocksApi.js';

function StockSearchBar({ compact = false }) {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const searchWrapRef = useRef(null);

  const normalizedKeyword = useMemo(() => keyword.trim(), [keyword]);

  useEffect(() => {
    if (!normalizedKeyword) {
      setResults([]);
      setOpen(false);
      return undefined;
    }

    const timerId = window.setTimeout(async () => {
      setLoading(true);
      try {
        const data = await stocksApi.searchStocks(normalizedKeyword);
        setResults(data);
        setOpen(true);
      } finally {
        setLoading(false);
      }
    }, 220);

    return () => window.clearTimeout(timerId);
  }, [normalizedKeyword]);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (!searchWrapRef.current?.contains(event.target)) {
        setOpen(false);
      }
    };

    window.addEventListener('mousedown', handleClickOutside);
    return () => window.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSubmit = (event) => {
    event.preventDefault();
    if (!normalizedKeyword) {
      return;
    }
    navigate(`/stocks?keyword=${encodeURIComponent(normalizedKeyword)}`);
    setOpen(false);
  };

  const handleSelect = (symbol) => {
    navigate(`/stocks/${symbol}`);
    setKeyword('');
    setOpen(false);
  };

  return (
    <div className={compact ? 'search-wrap compact' : 'search-wrap'} ref={searchWrapRef}>
      <form className="search-bar" onSubmit={handleSubmit}>
        <Search size={20} aria-hidden="true" />
        <input
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          onFocus={() => normalizedKeyword && setOpen(true)}
          placeholder="종목명 또는 심볼 검색"
          aria-label="종목명 또는 심볼 검색"
        />
        {keyword && (
          <button type="button" className="icon-button" onClick={() => setKeyword('')} aria-label="검색어 지우기">
            <X size={18} aria-hidden="true" />
          </button>
        )}
      </form>

      {open && (
        <div className="search-results" role="listbox">
          {loading && <div className="search-state">검색 중</div>}
          {!loading && results.length === 0 && <div className="search-state">결과 없음</div>}
          {!loading &&
            results.map((stock) => (
              <button key={stock.symbol} type="button" onClick={() => handleSelect(stock.symbol)} role="option">
                <span>
                  <strong>{stock.name}</strong>
                  <small>{stock.symbol}</small>
                </span>
                <em>{stock.market}</em>
              </button>
            ))}
        </div>
      )}
    </div>
  );
}

export default StockSearchBar;
