import { Area } from 'recharts/es6/cartesian/Area';
import { AreaChart } from 'recharts/es6/chart/AreaChart';
import { CartesianGrid } from 'recharts/es6/cartesian/CartesianGrid';
import { ReferenceLine } from 'recharts/es6/cartesian/ReferenceLine';
import { ResponsiveContainer } from 'recharts/es6/component/ResponsiveContainer';
import { Tooltip } from 'recharts/es6/component/Tooltip';
import { XAxis } from 'recharts/es6/cartesian/XAxis';
import { YAxis } from 'recharts/es6/cartesian/YAxis';

const priceFormatter = new Intl.NumberFormat('ko-KR', {
  maximumFractionDigits: 0,
});

const volumeFormatter = new Intl.NumberFormat('ko-KR', {
  notation: 'compact',
  maximumFractionDigits: 1,
});

function formatWon(value) {
  return `${priceFormatter.format(value)}원`;
}

function formatAxisPrice(value) {
  return priceFormatter.format(value);
}

function formatDateLabel(value) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return `${date.getMonth() + 1}/${date.getDate()}`;
}

function PriceTooltip({ active, payload, label }) {
  if (!active || !payload?.length) {
    return null;
  }

  const point = payload[0].payload;

  return (
    <div className="chart-tooltip">
      <strong>{label}</strong>
      <span>종가: {formatWon(point.close)}</span>
      <span>거래량: {volumeFormatter.format(point.volume)}주</span>
    </div>
  );
}

function PriceChart({ data }) {
  if (!data?.length) {
    return <div className="empty-panel">가격 데이터가 없습니다.</div>;
  }

  const startPrice = data[0].close;
  const latestPrice = data[data.length - 1].close;
  const values = data.map((point) => point.close);
  const highPrice = Math.max(...values);
  const lowPrice = Math.min(...values);
  const averagePrice = Math.round(values.reduce((sum, value) => sum + value, 0) / values.length);
  const changeRate = ((latestPrice - startPrice) / startPrice) * 100;
  const yPadding = Math.max(Math.round((highPrice - lowPrice) * 0.14), 1000);
  const yDomain = [Math.max(0, lowPrice - yPadding), highPrice + yPadding];

  const summaryItems = [
    ['시작가', formatWon(startPrice)],
    ['현재가', formatWon(latestPrice)],
    ['고가', formatWon(highPrice)],
    ['저가', formatWon(lowPrice)],
  ];

  return (
    <div className="chart-box">
      <div className="chart-summary" aria-label="가격 요약">
        {summaryItems.map(([label, value]) => (
          <div key={label} className="chart-summary-item">
            <span>{label}</span>
            <strong>{value}</strong>
          </div>
        ))}
        <div className="chart-summary-item">
          <span>기간 등락률</span>
          <strong className={changeRate >= 0 ? 'positive' : 'negative'}>{changeRate.toFixed(2)}%</strong>
        </div>
      </div>

      <div className="chart-viewport">
        <div className="chart-axis-guide" aria-label="차트 축 설명">
          <span>
            <strong>X축</strong> 날짜
          </span>
          <span>
            <strong>Y축</strong> 종가(원)
          </span>
          <span>
            <strong>점선</strong> 평균가
          </span>
        </div>
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 22, right: 30, left: 4, bottom: 38 }}>
            <defs>
              <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#0ecb81" stopOpacity={0.3} />
                <stop offset="95%" stopColor="#0ecb81" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#252d38" strokeDasharray="4 4" />
            <XAxis
              dataKey="date"
              tickFormatter={formatDateLabel}
              minTickGap={34}
              tick={{ fill: '#9aa4b2', fontSize: 12, fontWeight: 700 }}
              tickLine={{ stroke: '#3a4554' }}
              axisLine={{ stroke: '#3a4554' }}
              label={{
                value: '날짜',
                position: 'insideBottom',
                offset: -18,
                fill: '#9aa4b2',
                fontSize: 12,
                fontWeight: 800,
              }}
            />
            <YAxis
              domain={yDomain}
              tickCount={6}
              tickFormatter={formatAxisPrice}
              tick={{ fill: '#9aa4b2', fontSize: 12, fontWeight: 700 }}
              tickLine={{ stroke: '#3a4554' }}
              axisLine={{ stroke: '#3a4554' }}
              width={96}
              allowDecimals={false}
            />
            <Tooltip content={<PriceTooltip />} />
            <ReferenceLine
              y={averagePrice}
              stroke="#f0b90b"
              strokeDasharray="6 6"
              label={{
                value: `평균 ${formatWon(averagePrice)}`,
                position: 'insideTopRight',
                fill: '#f0b90b',
                fontSize: 12,
                fontWeight: 800,
              }}
            />
            <Area
              type="monotone"
              dataKey="close"
              name="종가"
              stroke="#0ecb81"
              strokeWidth={3}
              fill="url(#priceFill)"
              dot={false}
              activeDot={{ r: 6, stroke: '#0b0e11', strokeWidth: 2 }}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

export default PriceChart;
