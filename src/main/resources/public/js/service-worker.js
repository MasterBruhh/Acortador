const CACHE_NAME = 'url-shortener-cache-v2';
const OFFLINE_URL = '/offline.html';
const OFFLINE_PAGES = [
    '/',
    '/index',
    '/dashboard/urls',
    '/dashboard/urls-view',
    '/dashboard/urls-acortar',
    '/urls.html',
    '/urls-view.html',
    '/urls-acortar.html',
    '/js/indexLogic.js'
];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME).then(cache => {
            return cache.addAll(OFFLINE_PAGES);
        })
    );
});

self.addEventListener('fetch', event => {
    const { request } = event;

    if (request.mode === 'navigate') {
        event.respondWith(
            caches.match(request).then(cachedResponse => {
                return cachedResponse || fetch(request).catch(() => caches.match(OFFLINE_URL));
            })
        );
        return;
    }

    event.respondWith(
        fetch(request)
            .then(response => {
                const clone = response.clone();
                caches.open(CACHE_NAME).then(cache => cache.put(request, clone));
                return response;
            })
            .catch(() => caches.match(request))
    );
});