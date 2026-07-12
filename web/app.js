(() => {
  'use strict';

  const uploadButton = document.querySelector('#uploadButton');
  const fileInput = document.querySelector('#fileInput');
  const pasteButton = document.querySelector('#pasteButton');
  const pasteDialog = document.querySelector('#pasteDialog');
  const analyzeButton = document.querySelector('#analyzeButton');
  const contentInput = document.querySelector('#contentInput');
  const toast = document.querySelector('#toast');

  let toastTimer;

  function showToast(message) {
    window.clearTimeout(toastTimer);
    toast.textContent = message;
    toast.hidden = false;
    toastTimer = window.setTimeout(() => {
      toast.hidden = true;
    }, 2600);
  }

  uploadButton.addEventListener('click', () => fileInput.click());

  fileInput.addEventListener('change', () => {
    const [file] = fileInput.files;
    if (!file) return;
    showToast(`${file.name} is ready for analysis in the next build.`);
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

    pasteDialog.close();
    showToast('Content captured locally. Analysis arrives in the next verified build.');
    contentInput.value = '';
  });

  document.querySelectorAll('.card-actions button, .text-button, .bottom-nav a').forEach((control) => {
    control.addEventListener('click', (event) => {
      if (control.tagName === 'A') event.preventDefault();
      showToast('This workflow is part of the HOLD UP build plan.');
    });
  });
})();