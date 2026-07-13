(() => {
  'use strict';

  const STORAGE_KEY = 'hold-up-analysis-history-v1';
  const dashboard = document.querySelector('.dashboard');
  const emptyState = document.querySelector('#analysisEmptyState');
  const viewAllButton = document.querySelector('#viewAllAnalyses');
  let expanded = false;

  function readHistory() {
    try {
      const history = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      return Array.isArray(history) ? history : [];
    } catch {
      return [];
    }
  }

  function formatSavedTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return 'Saved on this device';
    return `Saved ${new Intl.DateTimeFormat(undefined, {
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit'
    }).format(date)}`;
  }

  function createHistoryCard(item) {
    const card = document.createElement('article');
    card.className = `action-card history-card ${item.category === 'risk' ? 'warning-card' : 'bill-card'}`;

    const icon = document.createElement('div');
    icon.className = 'card-icon';
    icon.setAttribute('aria-hidden', 'true');
    icon.textContent = item.icon || '•';

    const content = document.createElement('div');
    content.className = 'card-content';

    const kicker = document.createElement('div');
    kicker.className = 'card-kicker';
    kicker.textContent = `${item.kicker || 'Saved analysis'} · ${item.confidence || 'Unknown'} confidence`;

    const title = document.createElement('h3');
    title.textContent = item.title || 'Saved analysis';

    const summary = document.createElement('p');
    summary.textContent = item.summary || 'Review the original content before taking action.';

    const meta = document.createElement('p');
    meta.className = 'history-meta';
    meta.textContent = formatSavedTime(item.createdAt);

    content.append(kicker, title, summary, meta);
    card.append(icon, content);
    return card;
  }

  function renderHistory() {
    const history = readHistory();
    dashboard.querySelectorAll('.history-card').forEach((card) => card.remove());

    const existingLiveCard = dashboard.querySelector('.action-card:not(.history-card)');
    emptyState.hidden = history.length > 0 || Boolean(existingLiveCard);
    viewAllButton.hidden = history.length === 0;
    viewAllButton.textContent = expanded ? 'Show recent' : 'View all';

    if (existingLiveCard) return;

    const visible = expanded ? history : history.slice(0, 3);
    visible.forEach((item) => dashboard.append(createHistoryCard(item)));
  }

  viewAllButton?.addEventListener('click', () => {
    expanded = !expanded;
    renderHistory();
  });

  window.addEventListener('hold-up-history-cleared', renderHistory);

  const observer = new MutationObserver(() => {
    const hasCard = Boolean(dashboard.querySelector('.action-card'));
    emptyState.hidden = hasCard;
  });
  observer.observe(dashboard, { childList: true });

  renderHistory();
})();
