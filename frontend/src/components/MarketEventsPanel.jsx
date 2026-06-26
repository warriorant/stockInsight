import { CalendarDays, Megaphone } from '../icons.js';

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
});

function formatDate(value) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return dateFormatter.format(date);
}

function MarketEventsPanel({ events = [], compact = false, title = '시장 체크포인트' }) {
  if (!events.length) {
    return <div className="empty-panel">예정된 시장 이벤트가 없습니다.</div>;
  }

  return (
    <section className={compact ? 'event-panel compact' : 'event-panel'}>
      <div className="event-panel-head">
        <span className="eyebrow">
          <CalendarDays size={16} aria-hidden="true" />
          경제 일정
        </span>
        <h2>{title}</h2>
      </div>

      <div className="event-list">
        {events.map((event) => (
          <article className="event-card" key={event.id}>
            <div className="event-card-top">
              <div>
                <span className="event-date">{formatDate(event.scheduledDate)}</span>
                <h3>{event.title}</h3>
              </div>
              <strong className={event.importance === '높음' ? 'importance high' : 'importance'}>
                {event.importance}
              </strong>
            </div>

            <p>{event.summary}</p>
            <div className="beginner-note">
              <Megaphone size={16} aria-hidden="true" />
              <span>{event.beginnerImpact}</span>
            </div>

            <div className="event-tags">
              {event.relatedSectors.map((sector) => (
                <span key={sector}>{sector}</span>
              ))}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

export default MarketEventsPanel;
