const form = document.querySelector('#upload-form');
const images = document.querySelector('#images');
const statusText = document.querySelector('#status');

function status(message, error = false) {
  statusText.textContent = message;
  statusText.classList.toggle('error', error);
}

async function api(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || `Errore HTTP ${response.status}`);
  }
  return response;
}

function button(text, action) {
  const element = document.createElement('button');
  element.type = 'button';
  element.textContent = text;
  element.addEventListener('click', action);
  return element;
}

function download(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function card(asset) {
  const article = document.createElement('article');

  const preview = document.createElement('img');
  preview.src = `/api/images/${asset.id}/content`;
  preview.alt = asset.title;
  preview.loading = 'lazy';
  preview.addEventListener('error', () => {
    preview.replaceWith(document.createTextNode('Anteprima non disponibile'));
  });

  const title = document.createElement('h3');
  title.textContent = asset.title;
  const description = document.createElement('p');
  description.textContent = asset.description;
  const details = document.createElement('p');
  details.textContent = `${asset.format} · ${asset.width}×${asset.height} px · ${(asset.sizeBytes / 1024).toFixed(1)} KB`;

  const original = document.createElement('a');
  original.href = `/api/images/${asset.id}/content`;
  original.download = asset.originalFilename;
  original.textContent = 'Scarica originale';

  const actions = document.createElement('div');
  actions.className = 'actions';
  actions.append(
    original,
    button('Converti in PNG', async () => {
      try {
        const response = await api(`/api/images/${asset.id}/convert?format=PNG`, { method: 'POST' });
        download(await response.blob(), `${asset.title}.png`);
      } catch (error) {
        status(error.message, true);
      }
    }),
    button('Modifica', async () => {
      const title = prompt('Titolo', asset.title);
      if (title === null) return;
      const description = prompt('Descrizione', asset.description);
      if (description === null) return;
      try {
        await api(`/api/images/${asset.id}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ title, description })
        });
        await loadImages();
      } catch (error) {
        status(error.message, true);
      }
    }),
    button('Elimina', async () => {
      if (!confirm(`Eliminare “${asset.title}”?`)) return;
      try {
        await api(`/api/images/${asset.id}`, { method: 'DELETE' });
        await loadImages();
      } catch (error) {
        status(error.message, true);
      }
    })
  );

  article.append(preview, title, description, details, actions);
  return article;
}

async function loadImages() {
  try {
    const response = await api('/api/images');
    const assets = await response.json();
    images.replaceChildren(...(assets.length
      ? assets.map(card)
      : [document.createTextNode('Nessuna immagine caricata.')]));
    status('');
  } catch (error) {
    images.replaceChildren();
    status(`Catalogo non disponibile: ${error.message}`, true);
  }
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  try {
    await api('/api/images', { method: 'POST', body: new FormData(form) });
    form.reset();
    await loadImages();
    status('Immagine caricata.');
  } catch (error) {
    status(error.message, true);
  }
});

loadImages();
