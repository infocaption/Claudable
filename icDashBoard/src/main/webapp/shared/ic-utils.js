/**
 * InfoCaption Shared Utilities
 * ===========================
 * Common JavaScript utilities for dashboard modules.
 * Include this file in your module for shared functionality.
 */

const ICUtils = (function() {
    'use strict';

    // =====================================
    // Dashboard Communication
    // =====================================

    /**
     * Check if module is running inside dashboard iframe
     */
    function isInDashboard() {
        return window.parent !== window;
    }

    /**
     * Send message to parent dashboard
     * @param {string} type - Message type
     * @param {object} data - Message data
     */
    function sendToDashboard(type, data = {}) {
        if (isInDashboard()) {
            // Use same-origin instead of wildcard to prevent message leakage
            var targetOrigin = window.location.origin;
            window.parent.postMessage({ type, ...data }, targetOrigin);
        }
    }

    /**
     * Notify dashboard that module is ready
     * @param {string} moduleId - Module identifier
     */
    function notifyReady(moduleId) {
        sendToDashboard('MODULE_READY', { moduleId });
    }

    /**
     * Request dashboard to show module documentation popup
     * Shows the current module's description and AI spec text in a popup panel.
     * Only works when running inside the dashboard iframe.
     */
    function showDocPopup() {
        sendToDashboard('SHOW_MODULE_DOC');
    }

    /**
     * Request dashboard to show a toast notification
     * @param {string} message - Toast message
     * @param {string} type - 'success' or 'error'
     */
    function showToast(message, type = 'success') {
        if (isInDashboard()) {
            sendToDashboard('SHOW_TOAST', { message, toastType: type });
        } else {
            // Fallback: show local toast
            showLocalToast(message, type);
        }
    }

    /**
     * Show a local toast (when not in dashboard)
     */
    function showLocalToast(message, type = 'success') {
        // Create toast element if it doesn't exist
        let toast = document.getElementById('ic-toast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'ic-toast';
            toast.className = 'ic-toast';
            toast.innerHTML = '<span class="toast-icon"></span><span class="toast-message"></span>';
            document.body.appendChild(toast);
        }

        const icon = toast.querySelector('.toast-icon');
        const msg = toast.querySelector('.toast-message');

        icon.textContent = type === 'success' ? '✅' : '❌';
        msg.textContent = message;

        toast.style.background = type === 'success'
            ? 'linear-gradient(135deg, #48bb78 0%, #276749 100%)'
            : 'linear-gradient(135deg, #fc8181 0%, #c53030 100%)';

        toast.classList.add('show');
        setTimeout(() => toast.classList.remove('show'), 3000);
    }

    // =====================================
    // Data Utilities
    // =====================================

    /**
     * Format number with thousand separators
     * @param {number} num - Number to format
     * @returns {string} Formatted number
     */
    function formatNumber(num) {
        if (num === null || num === undefined) return '0';
        return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
    }

    /**
     * Calculate percentage change between two values
     * @param {number} current - Current value
     * @param {number} previous - Previous value
     * @returns {number} Percentage change
     */
    function calculateChange(current, previous) {
        if (!previous || previous === 0) {
            return current > 0 ? 100 : 0;
        }
        return ((current - previous) / previous) * 100;
    }

    /**
     * Format percentage with sign
     * @param {number} value - Percentage value
     * @returns {string} Formatted percentage
     */
    function formatPercent(value) {
        const sign = value >= 0 ? '+' : '';
        return `${sign}${Math.round(value)}%`;
    }

    /**
     * Format date to Swedish locale
     * @param {Date|string} date - Date to format
     * @returns {string} Formatted date
     */
    function formatDate(date) {
        const d = new Date(date);
        return d.toLocaleDateString('sv-SE');
    }

    /**
     * Format datetime to Swedish locale
     * @param {Date|string} date - Date to format
     * @returns {string} Formatted datetime
     */
    function formatDateTime(date) {
        const d = new Date(date);
        return d.toLocaleString('sv-SE');
    }

    // =====================================
    // URL Utilities
    // =====================================

    /**
     * Normalize URL for comparison
     * @param {string} url - URL to normalize
     * @returns {string} Normalized URL
     */
    function normalizeUrl(url) {
        if (!url) return '';
        return url.toLowerCase()
            .replace(/^https?:\/\//, '')
            .replace(/\/.*$/, '')
            .replace(/\/$/, '');
    }

    /**
     * Get query parameter from URL
     * @param {string} name - Parameter name
     * @returns {string|null} Parameter value
     */
    function getQueryParam(name) {
        const params = new URLSearchParams(window.location.search);
        return params.get(name);
    }

    /**
     * Set query parameter in URL (without reload)
     * @param {string} name - Parameter name
     * @param {string} value - Parameter value
     */
    function setQueryParam(name, value) {
        const url = new URL(window.location);
        if (value) {
            url.searchParams.set(name, value);
        } else {
            url.searchParams.delete(name);
        }
        window.history.pushState({}, '', url);
    }

    // =====================================
    // Storage Utilities
    // =====================================

    /**
     * Save data to localStorage with JSON serialization
     * @param {string} key - Storage key
     * @param {*} data - Data to store
     */
    function saveToStorage(key, data) {
        try {
            localStorage.setItem(key, JSON.stringify(data));
        } catch (e) {
            console.error('Failed to save to localStorage:', e);
        }
    }

    /**
     * Load data from localStorage with JSON parsing
     * @param {string} key - Storage key
     * @param {*} defaultValue - Default value if not found
     * @returns {*} Stored data or default
     */
    function loadFromStorage(key, defaultValue = null) {
        try {
            const item = localStorage.getItem(key);
            return item ? JSON.parse(item) : defaultValue;
        } catch (e) {
            console.error('Failed to load from localStorage:', e);
            return defaultValue;
        }
    }

    /**
     * Remove data from localStorage
     * @param {string} key - Storage key
     */
    function removeFromStorage(key) {
        try {
            localStorage.removeItem(key);
        } catch (e) {
            console.error('Failed to remove from localStorage:', e);
        }
    }

    // =====================================
    // DOM Utilities
    // =====================================

    /**
     * Create element with attributes and content
     * @param {string} tag - Element tag name
     * @param {object} attrs - Attributes object
     * @param {string|Node|Node[]} content - Inner content
     * @returns {HTMLElement} Created element
     */
    function createElement(tag, attrs = {}, content = '') {
        const el = document.createElement(tag);

        for (const [key, value] of Object.entries(attrs)) {
            if (key === 'className') {
                el.className = value;
            } else if (key.startsWith('on') && typeof value === 'function') {
                el.addEventListener(key.slice(2).toLowerCase(), value);
            } else if (key === 'dataset') {
                for (const [dataKey, dataValue] of Object.entries(value)) {
                    el.dataset[dataKey] = dataValue;
                }
            } else {
                el.setAttribute(key, value);
            }
        }

        if (typeof content === 'string') {
            el.innerHTML = content;
        } else if (Array.isArray(content)) {
            content.forEach(child => el.appendChild(child));
        } else if (content instanceof Node) {
            el.appendChild(content);
        }

        return el;
    }

    /**
     * Debounce function execution
     * @param {function} func - Function to debounce
     * @param {number} wait - Debounce delay in ms
     * @returns {function} Debounced function
     */
    function debounce(func, wait = 300) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }

    // =====================================
    // Export Utilities
    // =====================================

    /**
     * Export data to CSV file
     * @param {Array} data - Array of objects
     * @param {string} filename - Output filename
     * @param {Array} columns - Column definitions [{key, header}]
     */
    function exportToCSV(data, filename, columns) {
        const headers = columns.map(c => c.header).join(',');
        const rows = data.map(item =>
            columns.map(c => {
                const value = item[c.key] ?? '';
                // Escape quotes and wrap in quotes if contains comma
                const str = String(value);
                return str.includes(',') || str.includes('"')
                    ? `"${str.replace(/"/g, '""')}"`
                    : str;
            }).join(',')
        );

        const csvContent = '\uFEFF' + headers + '\n' + rows.join('\n');
        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = filename;
        link.click();
    }

    /**
     * Copy text to clipboard
     * @param {string} text - Text to copy
     * @returns {Promise<boolean>} Success status
     */
    async function copyToClipboard(text) {
        try {
            await navigator.clipboard.writeText(text);
            return true;
        } catch (err) {
            console.error('Failed to copy:', err);
            return false;
        }
    }

    // =====================================
    // Public API
    // =====================================

    return {
        // Dashboard communication
        isInDashboard,
        sendToDashboard,
        notifyReady,
        showToast,
        showDocPopup,

        // Data utilities
        formatNumber,
        calculateChange,
        formatPercent,
        formatDate,
        formatDateTime,

        // URL utilities
        normalizeUrl,
        getQueryParam,
        setQueryParam,

        // Storage utilities
        saveToStorage,
        loadFromStorage,
        removeFromStorage,

        // DOM utilities
        createElement,
        debounce,

        // Export utilities
        exportToCSV,
        copyToClipboard
    };
})();

// =====================================
// CSRF Token for iframe modules
// =====================================
// Modules loaded as iframes don't have the dashboard's global fetch() wrapper.
// This installs a similar wrapper that injects X-CSRF-Token on state-changing requests.
// Token is received from the parent dashboard via postMessage.
(function() {
    var _csrfToken = null;
    var _csrfResolvers = [];

    // Request CSRF token from parent dashboard
    function requestCsrfToken() {
        if (window.parent === window) return; // Not in iframe
        window.parent.postMessage({ type: 'GET_CSRF_TOKEN' }, window.location.origin);
    }

    // Listen for CSRF token response from parent
    window.addEventListener('message', function(event) {
        if (event.origin !== window.location.origin) return;
        if (event.data && event.data.type === 'CSRF_TOKEN') {
            _csrfToken = event.data.token;
            // Resolve any pending requests waiting for the token
            var resolvers = _csrfResolvers.splice(0);
            resolvers.forEach(function(r) { r(_csrfToken); });
        }
    });

    // Get token (returns immediately if cached, else requests from parent)
    function getCsrfToken() {
        if (_csrfToken) return Promise.resolve(_csrfToken);
        return new Promise(function(resolve) {
            _csrfResolvers.push(resolve);
            requestCsrfToken();
            // Timeout fallback: resolve with empty after 2s
            setTimeout(function() { resolve(_csrfToken || ''); }, 2000);
        });
    }

    // Install global fetch wrapper for iframe modules
    if (window.parent !== window) {
        var originalFetch = window.fetch;
        window.fetch = function(url, options) {
            options = options || {};
            var method = (options.method || 'GET').toUpperCase();

            // Only inject token for state-changing methods
            if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
                return getCsrfToken().then(function(token) {
                    if (token) {
                        if (!options.headers) options.headers = {};
                        if (options.headers instanceof Headers) {
                            if (!options.headers.has('X-CSRF-Token')) {
                                options.headers.set('X-CSRF-Token', token);
                            }
                        } else {
                            if (!options.headers['X-CSRF-Token']) {
                                options.headers['X-CSRF-Token'] = token;
                            }
                        }
                    }
                    return originalFetch.call(window, url, options);
                });
            }
            return originalFetch.call(window, url, options);
        };

        // Request token early so it's ready when needed
        requestCsrfToken();
    }

    // Expose for manual use
    ICUtils.getCsrfToken = getCsrfToken;
})();

// Listen for dashboard messages (with origin validation)
window.addEventListener('message', function(event) {
    // Only accept messages from same origin to prevent cross-origin attacks
    if (event.origin !== window.location.origin) return;

    const data = event.data;
    if (!data || !data.type) return;

    switch (data.type) {
        case 'DASHBOARD_COMMAND':
            // Custom event for modules to listen to
            window.dispatchEvent(new CustomEvent('dashboardCommand', { detail: data }));
            break;

        case 'THEME_CHANGE':
            document.documentElement.setAttribute('data-theme', data.theme);
            break;
    }
});
