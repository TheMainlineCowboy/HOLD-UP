(() => {
  'use strict';

  const uploadButton = document.querySelector('#uploadButton');
  const fileInput = document.querySelector('#fileInput');
  const pasteButton = document.querySelector('#pasteButton');
  const pasteDialog = document.querySelector('#pasteDialog');
  const analyzeButton = document.querySelector('#analyzeButton');
  const contentInput = document.querySelector('#contentInput');
  const toast = document.querySelector('#toast');
  const dashboard = document.querySelector('.dashboard');

  const STORAGE_KEY = 'hold-up-analysis-history-v1';
  let toastTimer;

  const patterns = {
    urgency: /\b(urgent|immediately|act now|final notice|last chance|today only|within \d+ (?:hours?|minutes?)|suspended|locked|warrant|arrest)\b/i,
    payment: /\b(pay|payment|send money|wire|gift card|crypto|bitcoin|cash app|venmo|zelle|bank transfer|routing number)\b/i,
    credential: /\b(password|passcode|verification code|one[- ]time code|otp|social security|ssn|login|sign in|confirm your account)\b/i,
    suspiciousLink: /https?:\/\/[^\s]+/i,
    bill: /\b(bill|invoice|statement|balance due|amount due|past due|utility|rent|mortgage|autopay)\b/i,
    subscription: /\b(subscription|membership|free trial|trial ends|renewal|renews|recurring|monthly plan|annual plan|cancel anytime)\b/i,
    appointment: /\b(appointment|reservation|meeting|visit|check[- ]?in|scheduled for|calendar)\b/i,
    cancellation: /\b(cancel|cancellation|unsubscribe|end membership|stop renewal)\b/i,
    priceChange: /\b(price (?:change|increase)|new rate|rate increase|will increase|changing from)\b/i
  };

  function showToast(message) {
    window.clearTimeout(toastTimer);
    toast.textContent = message;
    toast.hidden = false;
    toastTimer = window.setTimeout(() => {
      toast.hidden = true;
    }, 3200);
  }

  function extractAmount(text) {
    const match = text.match(/(?:\$|USD\s?)(\d{1,6}(?:,\d{3})*(?:\.\d{2})?)/i);
    return match ? `$${match[1]}` : null;
  }

  function extractDate(text) {
    const monthName = '(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)';
    const candidates = [
      new RegExp(`\\b${monthName}\\s+\\d{1,2}(?:,\\s*\\d{4})?\\b`, 'i'),
      /\b\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?\b/,
      /\b(?:today|tomorrow|tonight|next (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|week|month))\b/i
    ];

    for (const candidate of candidates) {
      const match = text.match(candidate);
      if (match) return match[0];
    }
    return null;
  }

  function analyzeText(text) {
    const signals = Object.fromEntries(
      Object.entries(patterns).map(([name, pattern]) => [name, pattern.test(text)])
    );
    const amount = extractAmount(text);
    const date = extractDate(text);
    const riskScore = [signals.urgency, signals.payment, signals.credential, signals.suspiciousLink]
      .filter(Boolean).length;

    if (riskScore >= 2 || (signals.credential && signals.suspiciousLink)) {
      return {
        category: 'risk',
        kicker: 'Potential risk',
        title: 'Pause before taking action',
        summary: signals.credential
          ? 'This message asks for sensitive account information. Verify the sender using a trusted phone number or app you open yourself.'
          : 'This message combines pressure with a payment or link request. Verify it independently before sending money or opening anything.',
        icon: '!',
        primaryAction: 'Verify safely',
        secondaryAction: 'Save evidence',
        amount,
        date,
        confidence: riskScore >= 3 ? 'High' : 'Medium'
      };
    }

    if (signals.subscription || signals.cancellation || signals.priceChange) {
      return {
        category: 'bill',
        kicker: signals.priceChange ? 'Price change' : 'Subscription',
        title: signals.cancellation ? 'Cancellation details found' : 'Recurring charge detected',
        summary: `${amount ? `${amount} appears in this notice. ` : ''}${date ? `A relevant date is ${date}. ` : ''}Review the merchant and terms before saving a renewal or cancellation reminder.`.trim(),
        icon: '$',
        primaryAction: signals.cancellation ? 'Review cancellation' : 'Track renewal',
        secondaryAction: 'Save details',
        amount,
        date,
        confidence: amount || date ? 'High' : 'Medium'
      };
    }

    if (signals.bill) {
      return {
        category: 'bill',
        kicker: 'Bill or invoice',
        title: amount ? `${amount} payment detected` : 'Payment due information found',
        summary: date ? `The notice references ${date}. Confirm the amount and due date before creating a reminder.` : 'Confirm the amount and due date before creating a reminder.',
        icon: date ? String(date.match(/\d{1,2}/)?.[0] || '•') : '•',
        primaryAction: 'Set reminder',
        secondaryAction: 'Edit details',
        amount,
        date,
        confidence: amount || date ? 'High' : 'Medium'
      };
    }

    if (signals.appointment || date) {
      return {
        category: 'bill',
        kicker: 'Date or appointment',
        title: date ? `Event found for ${date}` : 'Appointment details found',
        summary: 'Check the time, location, and contact details before adding this to your calendar.',
        icon: '◷',
        primaryAction: 'Add to calendar',
        secondaryAction: 'Review details',
        amount,
        date,
        confidence: date ? 'High' : 'Medium'
      };
    }

    return {
      category: 'neutral',
      kicker: 'Information only',
      title: 'No urgent action detected',
      summary: 'HOLD UP did not find a clear payment, deadline, subscription, appointment, or high-risk request. Read the original carefully before acting.',
      icon: '✓',
      primaryAction: 'Keep for reference',
      secondaryAction: 'Analyze another',
      amount,
      date,
      confidence: 'Low'
    };
  }

  function saveAnalysis(result, sourceText) {
    try {
      const history = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      history.unshift({
        ...result,
        sourcePreview: sourceText.slice(0, 180),
        createdAt: new Date().toISOString()
      });
      localStorage.setItem(STORAGE_KEY, JSON.stringify(history.slice(0, 20)));
    } catch {
      // Analysis still works when storage is unavailable or full.
    }
  }

  function createAnalysisCard(result) {
    const card = document.createElement('article');
    card.className = `action-card ${result.category === 'risk' ? 'warning-card' : 'bill-card'}`;
    card.setAttribute('aria-live', 'polite');

    const icon = document.createElement('div');
    icon.className = 'card-icon';
    icon.setAttribute('aria-hidden', 'true');
    icon.textContent = result.icon;

    const content = document.createElement('div');
    content.className = 'card-content';

    const kicker = document.createElement('div');
    kicker.className = 'card-kicker';
    kicker.textContent = `${result.kicker} · ${result.confidence} confidence`;

    const title = document.createElement('h3');
    title.textContent = result.title;

    const summary = document.createElement('p');
    summary.textContent = result.summary;

    const actions = document.createElement('div');
    actions.className = 'card-actions';

    const primary = document.createElement('button');
    primary.type = 'button';
    primary.textContent = result.primaryAction;
    primary.addEventListener('click', () => showToast(`${result.primaryAction} is saved as the next product workflow to complete.`));

    const secondary = document.createElement('button');
    secondary.type = 'button';
    secondary.className = 'ghost';
    secondary.textContent = result.secondaryAction;
    secondary.addEventListener('click', () => {
      if (result.secondaryAction === 'Analyze another') {
        pasteButton.click();
      } else {
        showToast('Details saved privately on this device.');
      }
    });

    actions.append(primary, secondary);
    content.append(kicker, title, summary, actions);
    card.append(icon, content);
    return card;
  }

  function renderAnalysis(result) {
    dashboard.querySelectorAll('.action-card').forEach((card) => card.remove());
    dashboard.append(createAnalysisCard(result));
    dashboard.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  uploadButton.addEventListener('click', () => fileInput.click());

  fileInput.addEventListener('change', () => {
    const [file] = fileInput.files;
    if (!file) return;
    showToast(`${file.name} stays on this device. Image and PDF text recognition is the next native milestone.`);
    fileInput.value = '';
  });

  pasteButton.addEventListener('click', () => {
    if (typeof pasteDialog.showModal === 'function') {
      pasteDialog.showModal();
      window.setTimeout(() => contentInput.focus(), 0);
      return;
    }
    showToast('Paste analysis requires a newer browser.');
  });

  analyzeButton.addEventListener('click', () => {
    const value = contentInput.value.trim();
    if (!value) {
      showToast('Paste a message, bill, email, or link first.');
      contentInput.focus();
      return;
    }

    const result = analyzeText(value);
    saveAnalysis(result, value);
    renderAnalysis(result);
    pasteDialog.close();
    contentInput.value = '';
    showToast('Analyzed privately on this device. No content was uploaded.');
  });

  document.querySelector('.text-button')?.addEventListener('click', () => {
    try {
      const count = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]').length;
      showToast(count ? `${count} private ${count === 1 ? 'analysis is' : 'analyses are'} saved on this device.` : 'No saved analyses yet.');
    } catch {
      showToast('Saved history is unavailable in this browser.');
    }
  });

  document.querySelectorAll('.bottom-nav a').forEach((control) => {
    control.addEventListener('click', (event) => {
      event.preventDefault();
      showToast('This section is planned for the next verified product increment.');
    });
  });

  document.querySelectorAll('.action-card .card-actions button').forEach((control) => {
    control.addEventListener('click', () => showToast('This example workflow is part of the HOLD UP build plan.'));
  });
})();
