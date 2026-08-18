import { Link } from 'react-router-dom';
import { ArrowUpRight, TrendingDown, TrendingUp } from '../icons.js';

const currencyFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
});

function StockCard({ stock }) {
  const isPositive = Number(stock.changeRate) >= 0;
  const hasPrice = Number(stock.currentPrice) > 0;
  const hasChangeRate = stock.changeRate !== null && stock.changeRate !== undefined;

  return (
    <Link to={`/stocks/${stock.symbol}`} className="stock-card">
      <div className="stock-card-header">
        <div>
          <strong>{stock.name}</strong>
          <span>
            {stock.symbol} · {stock.market}
          </span>
        </div>
        <ArrowUpRight size={20} aria-hidden="true" />
      </div>

      <div className="stock-price-row">
        <span>{hasPrice ? currencyFormatter.format(stock.currentPrice) : '상세에서 확인'}</span>
        {hasPrice && hasChangeRate && (
          <em className={isPositive ? 'positive' : 'negative'}>
            {isPositive ? <TrendingUp size={16} aria-hidden="true" /> : <TrendingDown size={16} aria-hidden="true" />}
            {stock.changeRate}%
          </em>
        )}
      </div>

      <div className="stock-meta">
        <span>{stock.sector}</span>
        <span>{stock.industry}</span>
      </div>
    </Link>
  );
}

export default StockCard;
