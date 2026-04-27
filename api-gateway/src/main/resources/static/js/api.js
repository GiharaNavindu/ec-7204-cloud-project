/**
 * API Utility for AuctionHub
 * Handles authentication, requests to the Spring Cloud API Gateway, and JWT extraction.
 */

const BASE_URL = window.location.origin;

/**
 * Robust JWT decoder that handles Unicode and edge cases.
 */
function parseJwt(token) {
    try {
        if (!token) return null;
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).slice(0).join(''));
        return JSON.parse(jsonPayload);
    } catch (e) {
        console.error("Failed to decode JWT:", e);
        return null;
    }
}

const API = {
    // Auth Management
    getToken: () => localStorage.getItem('token'),
    
    /**
     * Stores the token and immediately derives the user state from its claims.
     */
    setToken: (token) => {
        localStorage.setItem('token', token);
        const decoded = parseJwt(token);
        if (decoded) {
            const userState = { 
                email: decoded.sub, 
                userId: decoded.userId, 
                role: decoded.role 
            };
            localStorage.setItem('user', JSON.stringify(userState));
        }
    },

    removeToken: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
    },

    /**
     * Returns the user state. If the user object is missing but a token exists,
     * it re-decodes the token to restore the state.
     */
    getUser: () => {
        let user = JSON.parse(localStorage.getItem('user') || 'null');
        if (!user) {
            const token = API.getToken();
            if (token) {
                const decoded = parseJwt(token);
                if (decoded) {
                    user = { 
                        email: decoded.sub, 
                        userId: decoded.userId, 
                        role: decoded.role 
                    };
                    localStorage.setItem('user', JSON.stringify(user));
                }
            }
        }
        return user;
    },

    isAuthenticated: () => !!localStorage.getItem('token'),

    /**
     * Core request wrapper that automatically injects the JWT and handles
     * security-related HTTP status codes from the Gateway.
     */
    request: async (endpoint, options = {}) => {
        const token = API.getToken();
        
        const defaultHeaders = {
            'Content-Type': 'application/json',
            ...(token && { 'Authorization': `Bearer ${token}` })
        };

        const config = {
            ...options,
            headers: {
                ...defaultHeaders,
                ...options.headers
            }
        };

        try {
            const response = await fetch(`${BASE_URL}${endpoint}`, config);
            
            // Handle 401: Token expired or invalid
            if (response.status === 401) {
                // Only redirect if we're not already on the login page
                if (!window.location.pathname.endsWith('index.html') && window.location.pathname !== '/') {
                    API.removeToken();
                    window.location.href = '/index.html';
                }
                const data = await response.json().catch(() => ({}));
                throw { status: 401, message: data.message || 'Session expired. Please log in again.' };
            }

            // Handle 403: Forbidden (Authenticated but no permissions)
            if (response.status === 403) {
                throw { status: 403, message: 'Forbidden: You do not have permission to perform this action.' };
            }

            const contentType = response.headers.get('content-type');
            let data;
            if (contentType && contentType.includes('application/json')) {
                data = await response.json();
            } else {
                data = await response.text();
            }

            if (!response.ok) {
                throw { 
                    status: response.status, 
                    message: (data && data.message) ? data.message : (typeof data === 'string' ? data : 'Request failed') 
                };
            }

            return data;
        } catch (error) {
            console.error(`API Error [${endpoint}]:`, error);
            throw error;
        }
    },

    auth: {
        login: (email, password) => API.request('/api/users/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        }),
        register: (userData) => API.request('/api/users/register', {
            method: 'POST',
            body: JSON.stringify(userData)
        }),
        logout: () => {
            API.removeToken();
            window.location.href = '/index.html';
        }
    },

    auctions: {
        getAll: () => API.request('/api/auctions'),
        getById: (id) => API.request(`/api/auctions/${id}`),
        create: (auctionData) => {
            const user = API.getUser();
            if (!user || !user.userId) {
                throw new Error("Authentication error: User ID could not be resolved from session.");
            }
            return API.request('/api/auctions', {
                method: 'POST',
                body: JSON.stringify({
                    ...auctionData,
                    createdByUserId: user.userId
                })
            });
        },
        updateStatus: (id, status) => API.request(`/api/auctions/${id}/status?status=${status}`, {
            method: 'PATCH'
        })
    },

    bids: {
        place: (auctionId, amount) => API.request('/api/bids', {
            method: 'POST',
            body: JSON.stringify({ auctionId, amount })
        }),
        getHistory: (auctionId) => API.request(`/api/bids/auction/${auctionId}`),
        getUserBids: (userId) => API.request(`/api/bids/user/${userId}`)
    }
};

/**
 * Global UI Helpers for consistent data display and user feedback.
 */
const UI = {
    showToast: (message, type = 'info') => {
        const existing = document.getElementById('toast-container');
        if (existing) existing.remove();

        const toast = document.createElement('div');
        toast.id = 'toast-container';
        toast.className = `fixed bottom-4 right-4 px-6 py-4 rounded-xl shadow-2xl transform transition-all duration-300 translate-y-20 opacity-0 z-[9999] text-white font-bold flex items-center gap-3 ${
            type === 'error' ? 'bg-red-600 border border-red-500' : type === 'success' ? 'bg-green-600 border border-green-500' : 'bg-orange-500 border border-orange-400'
        }`;
        
        let icon = type === 'error' ? '⚠️' : type === 'success' ? '✅' : 'ℹ️';
        toast.innerHTML = `<span>${icon}</span> <span>${message}</span>`;
        
        document.body.appendChild(toast);
        setTimeout(() => { toast.classList.remove('translate-y-20', 'opacity-0'); }, 100);
        setTimeout(() => { toast.classList.add('translate-y-20', 'opacity-0'); setTimeout(() => toast.remove(), 300); }, 4000);
    },

    formatCurrency: (amount) => {
        return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount || 0);
    },

    formatDate: (dateString) => {
        if (!dateString) return 'N/A';
        return new Date(dateString).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    },

    getTimeRemaining: (endTime) => {
        if (!endTime) return { total: 0, days: 0, hours: 0, minutes: 0, seconds: 0 };
        const total = Date.parse(endTime) - Date.now();
        if (total <= 0) return { total: 0, days: 0, hours: 0, minutes: 0, seconds: 0 };
        return {
            total,
            days: Math.floor(total / (1000 * 60 * 60 * 24)),
            hours: Math.floor((total / (1000 * 60 * 60)) % 24),
            minutes: Math.floor((total / 1000 / 60) % 60),
            seconds: Math.floor((total / 1000) % 60)
        };
    }
};
