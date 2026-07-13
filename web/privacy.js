(() => {
  'use strict';

  const STORAGE_KEY = 'hold-up-analysis-history-v1';
  const settingsButton = document.querySelector('.topbar .icon-button');
  const dialog = document.querySelector('#privacyDialog');
  const savedCount = document.querySelector('#savedAnalysisCount');
  const clearButton = document.querySelector('#clearHistoryButton');
  const toast = document.querySelector('#toast');
  let toastTimer;

  function showToast(message) {
    window.clearTimeout(toastTimer);
    toast.textContent = message;
    toast.hidden = false;
    toastTimer = window.setTimeout(() => {
      toast.hidden = true;
    }, 3200);
  }

  function historyCount() {
    try {
      const history = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      return Array.isArray(history) ? history.length : 0;
    } catch {
      return 0;
    }
  }

  function refreshPrivacySummary() {
    const count = historyCount();
    savedCount.textContent = count === 1 ? '1 saved analysis' : `${count} saved analyses`;
    clearButton.disabled = count === 0;
  }

  settingsButton?.addEventListener('click', () => {
    refreshPrivacySummary();
    if (typeof dialog?.showModal === 'function') {
      dialog.showModal();
      return;
    }
    showToast('Privacy controls require a newer browser.');
  });

  clearButton?.addEventListener('click', () => {
    const count = historyCount();
    if (!count) return;

    const confirmed = window.confirm(
      `Delete ${count} private ${count === 1 ? 'analysis' : 'analyses'} from this browser? This cannot be undone.`
    );
    if (!confirmed) return;

    localStorage.removeItem(STORAGE_KEY);
    refreshPrivacySummary();
    window.dispatchEvent(new CustomEvent('hold-up-history-cleared'));
    dialog.close();
    showToast('Private analysis history deleted from this browser.');
  });
})();
