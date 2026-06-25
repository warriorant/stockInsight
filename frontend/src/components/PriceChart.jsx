import { Area } from 'recharts/es6/cartesian/Area';
import { AreaChart } from 'recharts/es6/chart/AreaChart';
import { CartesianGrid } from 'recharts/es6/cartesian/CartesianGrid';
import { ResponsiveContainer } from 'recharts/es6/component/ResponsiveContainer';
import { Tooltip } from 'recharts/es6/component/Tooltip';
import { XAxis } from 'recharts/es6/cartesian/XAxis';
import { YAxis } from 'recharts/es6/cartesian/YAxis';

const priceFormatter = new Intl.NumberFormat('ko-KR', {
  maximumFractionDigits: 0,
});

function PriceChart({ data }) {
  if (!data?.length) {
    return <div className="empty-panel">가격 데이터가 없습니다.</div>;
  }

  return (
    <div className="chart-box">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 16, right: 20, left: 2, bottom: 4 }}>
          <defs>
            <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#1f9d72" stopOpacity={0.28} />
              <stop offset="95%" stopColor="#1f9d72" stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="#e7edf3" strokeDasharray="4 4" vertical={false} />
          <XAxis dataKey="date" tickLine={false} axisLine={false} minTickGap={24} />
          <YAxis
            tickFormatter={(value) => priceFormatter.format(value)}
            tickLine={false}
            axisLine={false}
            width={72}
          />
          <Tooltip
            formatter={(value) => [`${priceFormatter.format(value)}원`, '종가']}
            labelFormatter={(label) => `날짜 ${label}`}
          />
          <Area type="monotone" dataKey="close" stroke="#1f9d72" strokeWidth={3} fill="url(#priceFill)" />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

export default PriceChart;
