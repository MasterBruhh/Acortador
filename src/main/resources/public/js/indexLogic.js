// === Función para saludar según la hora ===
function updateGreeting() {
    const now = new Date();
    const hour = now.getHours();
    let greeting = "Buen Dia!";
    let icon = "☀️";
    if (hour >= 12 && hour < 18) {
        greeting = "Buenas Tardes!";
    } else if (hour >= 18) {
        greeting = "Buenas Noches!";
        icon = "🌙";
    }
    const greetingText = document.getElementById('greetingText');
    const currentTime = document.getElementById('currentTime');
    if (greetingText) {
        const userSpan = greetingText.querySelector('span');
        const userName = userSpan ? userSpan.textContent : 'User';
        greetingText.innerHTML = `${greeting}, <span>${userName}</span>`;
    }
    if (currentTime) {
        currentTime.textContent = now.toLocaleString('es-ES', {
            weekday: 'short',
            month: 'short',
            day: 'numeric',
            hour: 'numeric',
            minute: '2-digit'
        });
    }
}
updateGreeting();
setInterval(updateGreeting, 60000);

// Variables globales para gráficos
window.currentAccessTimes = [];
window.currentShortUrl = null;

// Objeto global para guardar contenido de cada popup
window.popupContents = {};

// Obtiene el valor base desde el meta tag inyectado en la plantilla index.html.
const baseUrl = "https://bruhurl.azurewebsites.net";

// === Función para refrescar la lista de URLs ===
async function refreshUrlList() {
    const response = await fetch('/urls');
    let urlList = await response.json();

    // Comprobar que urlList es un array
    if (!Array.isArray(urlList)) {
        console.error("El endpoint /urls no devolvió un array", urlList);
        urlList = [];
    }

    // Obtener datos del usuario desde el elemento oculto
    const currentUserElem = document.getElementById("currentUser");
    const currentUsername = currentUserElem ? currentUserElem.getAttribute("data-username") : "";
    const currentUserRole = currentUserElem ? currentUserElem.getAttribute("data-role") : "anonymous";

    // Si el usuario no es admin, filtrar solo sus URLs
    if (currentUsername && currentUserRole !== "admin") {
        urlList = urlList.filter(item => item.user && item.user.username === currentUsername);
    }

    const tbody = document.getElementById('urlTableBody');
    tbody.innerHTML = '';

    // Por cada item, creamos una fila
    urlList.forEach(item => {
        const row = document.createElement('tr');
        row.classList.add('hover:bg-gray-100', 'transition-colors', 'duration-200');

        // Condicionar la inclusión del botón de estadísticas solo para usuarios autenticados
        let statsIconHtml = '';
        if (currentUserRole !== "anonymous") {
            statsIconHtml = `<span class="stats-icon text-3xl cursor-pointer ml-2" title="Ver estadísticas">
                                <i class="ri-line-chart-fill"></i>
                             </span>`;
        }

        row.innerHTML = `
            <td class="p-2 border preview-box justify-center">
                <i class="ri-qr-code-line qr-icon text-3xl cursor-pointer"></i>
                ${statsIconHtml}
            </td>
            <td class="p-2 border">${item.originalUrl.length > 35 ? item.originalUrl.substring(0, 35) + '…' : item.originalUrl}</td>
            <td class="p-2 border">
                <a href="${baseUrl}/go/${item.shortUrl}"
                   data-original-url="${item.originalUrl}"
                   class="shortLink text-blue-600 hover:underline" target="_blank">
                   ${baseUrl}/go/${item.shortUrl}
                </a>
            </td>
            <td class="p-2 border">${item.accessCount}</td>
        `;
        tbody.appendChild(row);

        // Guardamos un contenido de "cargando" temporal para la vista previa
        window.popupContents[item.shortUrl] = "Cargando vista previa...";

        // Obtener la preview de la URL para luego mostrarla en el modal
        fetch('/preview?url=' + encodeURIComponent(item.originalUrl))
            .then(response => response.ok ? response.json() : Promise.resolve(null))
            .then(previewData => {
                let previewHtml = '';
                if (previewData && previewData.data) {
                    const data = previewData.data;
                    previewHtml = `<strong>${data.title || 'Sin título'}</strong><br>`;
                    if (data.image && data.image.url) {
                        previewHtml += `<img src="${data.image.url}" alt="Preview" style="max-width:100px;">`;
                    } else {
                        previewHtml += `No image`;
                    }
                } else {
                    previewHtml = 'No preview disponible.';
                }

                // Generar el código QR usando un servicio externo (QRServer)
                const shortUrlFull = baseUrl + "/go/" + item.shortUrl;
                const qrUrl = "https://api.qrserver.com/v1/create-qr-code/?data=" +
                    encodeURIComponent(shortUrlFull) +
                    "&size=120x120";

                // Construir el HTML que se mostrará en el modal
                const finalHtml = `
                    <div style="display: flex; gap: 10px; align-items: center;">
                        <img src="${qrUrl}" alt="Código QR" />
                        <div>${previewHtml}</div>
                    </div>
                `;

                // Guardarlo en nuestro objeto global para el popup
                window.popupContents[item.shortUrl] = finalHtml;
            })
            .catch(error => {
                console.error(error);
                window.popupContents[item.shortUrl] = 'Error al obtener la preview.';
            });

        // Al hacer click en el icono QR, se muestra la vista previa en un modal
        const previewCell = row.querySelector('.preview-box');
        const qrIcon = previewCell.querySelector('.qr-icon');
        if (qrIcon) {
            qrIcon.addEventListener('click', function(e) {
                e.stopPropagation();
                openGlobalModal(window.popupContents[item.shortUrl]);
            });
        }

        // Agregar listener al icono de estadísticas solo si se renderizó
        if (currentUserRole !== "anonymous") {
            const statsIcon = row.querySelector('.stats-icon');
            if (statsIcon) {
                statsIcon.addEventListener('click', function(e) {
                    e.stopPropagation();
                    // Redirige a la página de estadísticas pasando shortUrl como parámetro
                    window.location.href = '/estadisticas?shortUrl=' + item.shortUrl;
                });
            }
        }
    });

    // Mostrar la sección de la lista solo si existen filas en la tabla
    if (tbody.children.length > 0) {
        document.getElementById('urlListSection').classList.remove('hidden');
    }
}

// Función para abrir el modal global con cierto HTML
function openGlobalModal(htmlContent) {
    const overlay = document.getElementById('globalModalOverlay');
    const modal = document.getElementById('globalModal');
    const content = document.getElementById('modalContent');
    overlay.classList.remove('hidden');
    modal.classList.remove('hidden');
    content.innerHTML = htmlContent;
}

// Función para cerrar el modal global
function closeGlobalModal() {
    const overlay = document.getElementById('globalModalOverlay');
    const modal = document.getElementById('globalModal');
    const content = document.getElementById('modalContent');
    overlay.classList.add('hidden');
    modal.classList.add('hidden');
    content.innerHTML = '';
}

// Asignamos event listeners para cerrar el modal
document.getElementById('closeModalBtn').addEventListener('click', closeGlobalModal);
document.getElementById('globalModalOverlay').addEventListener('click', closeGlobalModal);

// Función para obtener vista previa de la URL (ahora carga en el modal)
async function getPreview() {
    const urlValue = document.getElementById('inputUrl').value;
    if (!urlValue) {
        alert('Ingresa una URL para obtener la vista previa.');
        return;
    }
    const response = await fetch('/preview?url=' + encodeURIComponent(urlValue));
    let html = "<h4 class='font-semibold'>Vista Previa:</h4>";
    if (response.ok) {
        const previewData = await response.json();
        if (previewData.data) {
            const data = previewData.data;
            html += `<strong>Título:</strong> ${data.title || 'No disponible'}<br>`;
            html += `<strong>Descripción:</strong> ${data.description || 'No disponible'}<br>`;
            if (data.image && data.image.url) {
                html += `<img src="${data.image.url}" alt="Vista Previa" style="max-width:200px;"><br>`;
            }
        } else {
            html += "No se encontró información de vista previa.";
        }
    } else {
        html += 'Error al obtener la vista previa.';
    }
    openGlobalModal(html);
}

// Función para mostrar estadísticas de una URL
async function showStats(shortUrl) {
    window.currentShortUrl = shortUrl;
    await updateStats();
}

// Función para actualizar estadísticas y dibujar gráficos
async function updateStats() {
    if (window.currentShortUrl) {
        const response = await fetch('/stats/' + window.currentShortUrl);
        if (response.ok) {
            const statsData = await response.json();
            window.currentAccessTimes = statsData.accessTimes;
            drawChart();
            drawBrowserChart(statsData.browserStats);
        } else {
            alert('No se encontraron estadísticas para este enlace.');
        }
    }
}

function drawChart() {
    const timeRange = document.getElementById('timeRange')?.value || "all";
    let filtered = filterAccessTimes(window.currentAccessTimes, timeRange);
    filtered.sort((a, b) => new Date(a) - new Date(b));
    const labels = filtered.map(date => new Date(date).toLocaleString());
    const dataPoints = filtered.map((_, index) => index + 1);
    const ctx = document.getElementById('accessChart')?.getContext('2d');
    if (!ctx) return; // Si no hay canvas en esta página, salir
    if (window.myChart) {
        window.myChart.destroy();
    }
    window.myChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                label: 'Accesos en el tiempo',
                data: dataPoints,
                borderColor: 'blue',
                fill: false
            }]
        },
        options: {
            scales: {
                x: { title: { display: true, text: 'Fecha y Hora' } },
                y: { title: { display: true, text: 'Cantidad de Accesos' }, beginAtZero: true }
            }
        }
    });
}

function filterAccessTimes(accessTimes, range) {
    const now = new Date();
    let threshold;
    switch (range) {
        case 'today':
            threshold = new Date(now.getFullYear(), now.getMonth(), now.getDate());
            break;
        case 'week':
            threshold = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
            break;
        case 'month':
            threshold = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
            break;
        case '3months':
            threshold = new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000);
            break;
        case '6months':
            threshold = new Date(now.getTime() - 180 * 24 * 60 * 60 * 1000);
            break;
        case 'year':
            threshold = new Date(now.getTime() - 365 * 24 * 60 * 60 * 1000);
            break;
        default:
            threshold = new Date(0);
    }
    return accessTimes.filter(item => new Date(item) >= threshold);
}

function drawBrowserChart(browserStats) {
    const labels = Object.keys(browserStats);
    const data = Object.values(browserStats);
    const ctx = document.getElementById('browserChart')?.getContext('2d');
    if (!ctx) return; // Si no hay canvas en esta página, salir
    if (window.browserChart && typeof window.browserChart.destroy === 'function') {
        window.browserChart.destroy();
    }
    window.browserChart = new Chart(ctx, {
        type: 'pie',
        data: {
            labels: labels,
            datasets: [{
                data: data
            }]
        },
        options: {
            responsive: true,
            plugins: {
                legend: { position: 'top' }
            }
        }
    });
}

// Vincula el evento de click para el botón de vista previa (icono del ojo)
document.getElementById('previewBtn')?.addEventListener('click', getPreview);

// Manejo del formulario (submit => acortar enlace)
document.getElementById('urlForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    const formData = new FormData(this);
    const response = await fetch('/acortar', {
        method: 'POST',
        body: formData
    });
    const jsonResponse = await response.json();
    refreshUrlList();
});

// Carga inicial de la lista de URLs
refreshUrlList();

// Refresca la lista cada 30 segundos
setInterval(refreshUrlList, 30000);
// Refresca las estadísticas cada 30 segundos
setInterval(updateStats, 30000);

// Listener para el rango de tiempo en caso de que se use un select #timeRange
document.getElementById('timeRange')?.addEventListener('change', drawChart);
