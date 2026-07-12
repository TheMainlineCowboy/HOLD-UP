(() => {
  'use strict';

  const STORAGE_KEY = 'hold-up-analysis-history-v1';

  function readHistory() {
    try {
      const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function createRestoredCard(result) {
    const card = document.createElement('article');
    card.className = `action-card ${result.category === 'risk' ? 'warning-card' : 'bill-card'}`;
    card.dataset.restored = 'true';

    const icon = document.createElement('div');
    icon.className = 'card-icon';
    icon.setAttribute('aria-hidden', 'true');
    icon.textContent = result.icon || '•';

    const content = document.createElement('div');
    content.className = 'card-content';

    const kicker = document.createElement('div');
    kicker.className = 'card-kicker';
    kicker.textContent = `${result.kicker || 'Saved analysis'} · ${result.confidence || 'Unknown'} confidence`;

    const title = document.createElement('h3');
    title.textContent = result.title || 'Saved analysis';

    const summary = document.createElement('p');
    summary.textContent = result.summary || 'This analysis was restored from private storage on this device.';

    const actions = document.createElement('div');
    actions.className = 'card-actions';

    const primary = document.createElement('button');
    primary.type = 'button';
    primary.textContent = 'Review again';
    primary.addEventListener('click', () => document.querySelector('#pasteButton')?.click());

    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'ghost';
    remove.textContent = 'Remove from device';
    remove.addEventListener('click', () => {
      const history = readHistory();
      const updated = history.filter((item) => item.createdAt !== result.createdAt);
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
      } catch {
        return;
      }
      card.remove();
      document.querySelector('#privacyStatus')?.replaceChildren(document.createTextNode('Saved analysis removed from this device.'));
    });

    actions.append(primary, remove);
    content.append(kicker, title, summary, actions);
    card.append(icon, content);
    return card;
  }

  function restoreLatestAnalysis() {
    const [latest] = readHistory();
    const dashboard = document.querySelector('.dashboard');
    if (!latest || !dashboard || dashboard.querySelector('[data-restored="true"]')) return;

    dashboard.querySelectorAll('.action-card').forEach((card) => card.remove());
    dashboard.append(createRestoredCard(latest));

    const status = document.querySelector('#privacyStatus');
    if (status) status.textContent = 'Latest analysis restored from private storage on this device.';
  }

  function registerServiceWorker() {
    if (!('serviceWorker' in navigator)) return;

    window.addEventListener('load', () => {
      navigator.serviceWorker.register('./sw.js').catch(() => {
        const status = document.querySelector('#privacyStatus');
        if (status) status.textContent = 'Private analysis is available. Offline installation is not supported in this browser.';
      });
    });
  }

  restoreLatestAnalysis();
  registerServiceWorker();
})();
