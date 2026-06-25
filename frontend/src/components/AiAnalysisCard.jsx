import { Brain, RefreshCw, ShieldAlert, Sparkles, Target } from '../icons.js';

function AiAnalysisCard({ analysis, loading, onRefresh }) {
  if (!analysis) {
    return <div className="empty-panel">AI 분석 결과가 없습니다.</div>;
  }

  return (
    <article className="analysis-card">
      <div className="analysis-header">
        <div>
          <span className="eyebrow">
            <Brain size={16} aria-hidden="true" />
            AI 분석
          </span>
          <h2>{analysis.rating}</h2>
        </div>
        <div className="score-badge">
          <span>{analysis.score}</span>
          <small>/ 100</small>
        </div>
      </div>

      <p className="analysis-summary">{analysis.summary}</p>

      <div className="analysis-grid">
        <section>
          <Sparkles size={18} aria-hidden="true" />
          <h3>기술적 분석</h3>
          <p>{analysis.technicalAnalysis}</p>
        </section>
        <section>
          <Target size={18} aria-hidden="true" />
          <h3>기본적 분석</h3>
          <p>{analysis.fundamentalAnalysis}</p>
        </section>
        <section>
          <ShieldAlert size={18} aria-hidden="true" />
          <h3>리스크</h3>
          <p>{analysis.risk}</p>
        </section>
      </div>

      <button type="button" className="primary-button" onClick={onRefresh} disabled={loading}>
        <RefreshCw size={18} aria-hidden="true" />
        {loading ? '분석 중' : '분석 갱신'}
      </button>
    </article>
  );
}

export default AiAnalysisCard;
