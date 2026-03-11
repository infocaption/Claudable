/**
 * Service Worker for CloudGuard push notifications.
 * Registered from the cloudguard-monitor module.
 */

self.addEventListener('push', function(event) {
    if (!event.data) return;

    let data;
    try {
        data = event.data.json();
    } catch (e) {
        data = { title: 'CloudGuard', body: event.data.text() };
    }

    const severity = (data.severity || 'info').toLowerCase();
    const iconMap = {
        critical: '\u{1F534}',
        error:    '\u{1F7E0}',
        warning:  '\u{1F7E1}',
        info:     '\u{1F535}'
    };

    // Derive context path from service worker scope (e.g. "/icDashBoard/")
    const scope = self.registration.scope;
    const ctxPath = new URL(scope).pathname.replace(/\/$/, '');

    const options = {
        body: data.body || '',
        icon: ctxPath + '/shared/ic-logo-192.png',
        badge: ctxPath + '/shared/ic-logo-192.png',
        tag: data.tag || 'cloudguard-' + Date.now(),
        data: {
            incidentId: data.incidentId,
            severity: severity
        },
        actions: [
            { action: 'open', title: 'Visa' },
            { action: 'dismiss', title: 'Stang' }
        ],
        requireInteraction: severity === 'critical' || severity === 'error'
    };

    event.waitUntil(
        self.registration.showNotification(
            (iconMap[severity] || '') + ' ' + (data.title || 'CloudGuard'),
            options
        )
    );
});

self.addEventListener('notificationclick', function(event) {
    event.notification.close();

    if (event.action === 'dismiss') return;

    // Open or focus the dashboard with cloudguard-monitor
    const scope2 = self.registration.scope;
    const ctxPath2 = new URL(scope2).pathname.replace(/\/$/, '');
    const url = ctxPath2 + '/dashboard.jsp?module=cloudguard-monitor';

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(windowClients) {
            // If dashboard is already open, focus it
            for (var i = 0; i < windowClients.length; i++) {
                var client = windowClients[i];
                if (client.url.indexOf('/icDashBoard/dashboard') !== -1) {
                    client.focus();
                    client.postMessage({
                        type: 'cloudguard-notification-click',
                        incidentId: event.notification.data.incidentId
                    });
                    return;
                }
            }
            // Otherwise open a new window
            return clients.openWindow(url);
        })
    );
});

self.addEventListener('install', function(event) {
    self.skipWaiting();
});

self.addEventListener('activate', function(event) {
    event.waitUntil(self.clients.claim());
});
