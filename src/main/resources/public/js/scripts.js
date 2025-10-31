document.addEventListener('DOMContentLoaded', function () {
    // Dropdown
    const createBtn = document.getElementById('createBtn');
    const dropdownMenu = document.getElementById('dropdownMenu');

    // Mostrar y ocultar el dropdown
    createBtn.addEventListener('click', () => {
        dropdownMenu.classList.toggle('hidden');
    });

    // Modal y abrir la opción de crear link
    const openCreateLink = document.getElementById('openCreateLink');
    const modalCreateLink = document.getElementById('modalCreateLink');

    openCreateLink.addEventListener('click', () => {
        dropdownMenu.classList.add('hidden'); // Ocultar el dropdown
        modalCreateLink.classList.remove('hidden'); // Mostrar el modal
        modalCreateLink.classList.add('flex');
    });

    // Cerrar el modal
    const closeModalLink = document.getElementById('closeModalLink');
    closeModalLink.addEventListener('click', () => {
        modalCreateLink.classList.add('hidden');
        modalCreateLink.classList.remove('flex');
    });

    // Lógica para acortar URL
    const urlFormModal = document.getElementById('urlFormModal');
    urlFormModal.addEventListener('submit', async function(e) {
        e.preventDefault();
        const formData = new FormData(this);
        const response = await fetch('/acortar', {
            method: 'POST',
            body: formData
        });
        const json = await response.json();
        document.getElementById('resultadoModal').innerHTML = `<a href="${json.shortUrl}" target="_blank">${json.shortUrl}</a>`;
        document.getElementById('qrContainerModal').innerHTML = `<img src="${json.qrCode}" alt="QR" class="mt-2">`;
    });

    // Vista previa
    const previewBtnModal = document.getElementById('previewBtnModal');
    previewBtnModal.addEventListener('click', async () => {
        const urlValue = document.getElementById('inputUrlModal').value;
        if (!urlValue) {
            alert('Ingresa una URL primero');
            return;
        }
        const response = await fetch('/preview?url=' + encodeURIComponent(urlValue));
        if (response.ok) {
            const data = await response.json();
            let html = '<h4 class="font-semibold mb-1">Vista Previa:</h4>';
            if (data.data) {
                html += `<div><strong>${data.data.title || 'Sin título'}</strong><br>${data.data.description || 'Sin descripción'}`;
                if (data.data.image && data.data.image.url) html += `<br><img src="${data.data.image.url}" class="mt-2 max-w-xs">`;
                html += '</div>';
            } else {
                html += 'No se encontró vista previa.';
            }
            const previewContainerModal = document.getElementById('previewContainerModal');
            previewContainerModal.innerHTML = html;
            previewContainerModal.classList.remove('hidden');
        } else {
            alert('Error al obtener la vista previa');
        }
    });
});
