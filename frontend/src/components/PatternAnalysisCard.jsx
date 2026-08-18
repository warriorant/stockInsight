import { BarChart3, Brain, RefreshCw, ShieldAlert, Sparkles, Target } from '../icons.js';

const percentFormatter = new Intl.NumberFormat('ko-KR', {
  maximumFractionDigits: 1,
  signDisplay: 'exceptZero',
});

const plainPercentFormatter = new Intl.NumberFormat('ko-KR', {
  maximumFractionDigits: 0,
});

function formatReturn(value) {
  if (value === null || value === undefined) {
    return '-';
  }
  return `${percentFormatter.format(Number(value))}%`;
}

function formatRate(value) {
  if (value === null || value === undefined) {
    return '-';
  }
  return `${plainPercentFormatter.format(Number(value))}%`;
}

function formatConfidence(value) {
  if (value === null || value === undefined) {
    return '-';
  }
  return `${plainPercentFormatter.format(Number(value) * 100)}%`;
}

function displayPeriod(period) {
  if (period === '6M') return '6개월';
  if (period === '12M') return '12개월';
  if (period === '1Y') return '1년';
  return period;
}

function patternVisual(patternName) {
  if (patternName?.includes('상승 삼각형')) {
    return {
      flow: 'M42 205 L105 178 L170 190 L235 155 L300 165 L365 130 L430 138 L520 90 L598 78',
      guides: [
        'M86 82 L600 82',
        'M42 222 L560 95',
      ],
      endY: 78,
      label: '고점은 비슷하고 저점이 높아지는 형태',
    };
  }

  if (patternName?.includes('이중 바닥')) {
    return {
      flow: 'M42 92 C88 120 112 196 162 202 C210 208 230 114 282 104 C338 92 354 204 414 202 C475 200 510 118 598 90',
      guides: [
        'M128 205 L456 205',
        'M255 102 L600 102',
      ],
      endY: 90,
      label: '비슷한 가격대에서 두 번 하락이 멈춘 형태',
    };
  }

  if (patternName?.includes('박스권')) {
    return {
      flow: 'M42 135 L96 92 L160 184 L224 98 L292 178 L362 95 L430 174 L498 100 L598 150',
      guides: [
        'M42 86 L598 86',
        'M42 190 L598 190',
      ],
      endY: 150,
      label: '상단과 하단 사이를 반복해서 오가는 형태',
    };
  }

  if (patternName?.includes('이중 천장')) {
    return {
      flow: 'M42 190 C86 160 110 80 162 76 C214 72 230 166 280 178 C334 190 356 82 414 76 C480 70 520 150 598 190',
      guides: [
        'M128 74 L452 74',
        'M42 186 L598 186',
      ],
      endY: 190,
      label: '비슷한 가격대에서 두 번 상승이 막힌 형태',
    };
  }

  if (patternName?.includes('하락 채널')) {
    return {
      flow: 'M42 78 L112 112 L178 96 L245 142 L312 128 L382 170 L454 154 L524 205 L598 190',
      guides: [
        'M42 54 L598 168',
        'M42 120 L598 232',
      ],
      endY: 190,
      label: '고점과 저점이 함께 낮아지는 형태',
    };
  }

  return {
    flow: 'M42 170 C110 125 155 195 220 148 C285 102 330 176 395 126 C468 70 520 112 598 88',
    guides: ['M42 210 L598 82'],
    endY: 88,
    label: '최근 가격 흐름에서 가장 가까운 패턴을 찾는 예시',
  };
}

function PatternPreview({ patternName }) {
  const visual = patternVisual(patternName);

  return (
    <section className="pattern-visual-panel" aria-label={`${patternName} 패턴 예시 이미지`}>
      <div className="pattern-visual-copy">
        <span>패턴 모양 예시</span>
        <strong>{patternName}</strong>
        <p>{visual.label}</p>
      </div>
      <div className="pattern-visual-frame">
        <svg viewBox="0 0 640 260" role="img" aria-label={`${patternName} 차트 패턴 예시`}>
          <rect x="0" y="0" width="640" height="260" rx="8" />
          <g className="grid-lines">
            <path d="M40 48 L610 48" />
            <path d="M40 100 L610 100" />
            <path d="M40 152 L610 152" />
            <path d="M40 204 L610 204" />
          </g>
          <g className="guide-lines">
            {visual.guides.map((guide) => (
              <path d={guide} key={guide} />
            ))}
          </g>
          <path className="pattern-flow-shadow" d={visual.flow} />
          <path className="pattern-flow" d={visual.flow} />
          <circle className="pattern-last-point" cx="598" cy={visual.endY} r="5" />
        </svg>
        <div className="pattern-legend">
          <span>
            <i className="flow-dot" />
            가격 흐름
          </span>
          <span>
            <i className="guide-dot" />
            패턴 기준선
          </span>
        </div>
      </div>
    </section>
  );
}

function PatternAnalysisCard({ analysis, loading, onRefresh }) {
  if (!analysis) {
    return <div className="empty-panel">차트 패턴 분석 결과가 없습니다.</div>;
  }

  return (
    <article className="analysis-card pattern-card">
      <div className="analysis-header pattern-header">
        <div>
          <span className="eyebrow">
            <Brain size={16} aria-hidden="true" />
            AI 차트 패턴
          </span>
          <h2>{analysis.patternName}</h2>
          <p>{analysis.patternCategory}</p>
        </div>
        <div className="pattern-confidence">
          <span>패턴 인식 신뢰도</span>
          <strong>{formatConfidence(analysis.confidence)}</strong>
        </div>
      </div>

      <p className="analysis-summary">{analysis.summary}</p>

      <PatternPreview patternName={analysis.patternName} />

      {analysis.periodAnalyses?.length > 0 && (
        <section className="period-patterns">
          <div className="pattern-section-title">
            <BarChart3 size={18} aria-hidden="true" />
            <h3>기간별 AI 분류 결과</h3>
          </div>
          <div className="period-pattern-grid">
            {analysis.periodAnalyses.map((item) => (
              <article className="period-pattern-card" key={item.period}>
                <div>
                  <span>{displayPeriod(item.period)} 차트</span>
                  <strong>{item.patternName}</strong>
                  <em>패턴 {item.patternId}</em>
                </div>
                <dl>
                  <div>
                    <dt>신뢰도</dt>
                    <dd>{formatConfidence(item.confidence)}</dd>
                  </div>
                  <div>
                    <dt>참고 평균</dt>
                    <dd>{formatReturn(item.referenceReturns?.[0]?.averageReturn)}</dd>
                  </div>
                  <div>
                    <dt>상승 표본</dt>
                    <dd>{formatRate(item.referenceReturns?.[0]?.positiveRate)}</dd>
                  </div>
                </dl>
              </article>
            ))}
          </div>
        </section>
      )}

      <div className="pattern-detail-grid">
        <section>
          <Sparkles size={18} aria-hidden="true" />
          <h3>패턴 설명</h3>
          <p>{analysis.patternDescription}</p>
        </section>
        <section>
          <Target size={18} aria-hidden="true" />
          <h3>확인할 것</h3>
          <ul>
            {analysis.checkPoints?.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>
        <section>
          <ShieldAlert size={18} aria-hidden="true" />
          <h3>주의</h3>
          <p>{analysis.disclaimer}</p>
        </section>
      </div>

      <section className="pattern-backtest">
        <div className="pattern-section-title">
          <BarChart3 size={18} aria-hidden="true" />
          <h3>전문가 참고 수익률 경향</h3>
        </div>
        <div className="pattern-table" role="table" aria-label="전문가 참고 수익률 경향">
          <div className="pattern-table-row header" role="row">
            <span role="columnheader">기간</span>
            <span role="columnheader">평균</span>
            <span role="columnheader">중앙값</span>
            <span role="columnheader">상승 표본</span>
            <span role="columnheader">최대 하락</span>
          </div>
          {analysis.backtests?.map((item) => (
            <div className="pattern-table-row" role="row" key={item.period}>
              <span role="cell">{displayPeriod(item.period)}</span>
              <strong role="cell">{formatReturn(item.averageReturn)}</strong>
              <span role="cell">{formatReturn(item.medianReturn)}</span>
              <span role="cell">{formatRate(item.positiveRate)}</span>
              <em role="cell">{formatReturn(item.worstReturn)}</em>
            </div>
          ))}
        </div>
      </section>

      <section className="similar-patterns">
        <div className="pattern-section-title">
          <BarChart3 size={18} aria-hidden="true" />
          <h3>유사 사례</h3>
        </div>
        <div className="similar-case-grid">
          {analysis.similarCases?.map((item) => (
            <article className="similar-case" key={`${item.symbol}-${item.detectedDate}`}>
              <div>
                <strong>{item.name}</strong>
                <span>{item.detectedDate}</span>
              </div>
              <dl>
                <div>
                  <dt>단기</dt>
                  <dd>{formatReturn(item.returnAfter1M)}</dd>
                </div>
                <div>
                  <dt>6개월</dt>
                  <dd>{formatReturn(item.returnAfter3M)}</dd>
                </div>
                <div>
                  <dt>12개월</dt>
                  <dd>{formatReturn(item.returnAfter1Y)}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      </section>

      <button type="button" className="primary-button" onClick={onRefresh} disabled={loading}>
        <RefreshCw size={18} aria-hidden="true" />
        {loading ? '분석 중' : '패턴 다시 분석'}
      </button>
    </article>
  );
}

export default PatternAnalysisCard;
