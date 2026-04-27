/**
 * API Utility for AuctionHub
 * Handles authentication, requests to the Spring Cloud API Gateway, and JWT extraction.
 */

const BASE_URL = window.location.origin;

// Simple JWT decoder for browser
function parseJwt(token) {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
        return JSON.parse(jsonPayload);
    } catch (e) {
        return null;
    }
}

const API = {
    // Auth Management
    getToken: () => localStorage.getItem('token'),
    setToken: (token) => {
        localStorage.setItem('token', token);
        const decoded = parseJwt(token);
        if (decoded) {
            API.setUser({ 
                email: decoded.sub, 
                userId: decoded.userId, 
                role: decoded.role 
            });
        }
    },
    removeToken: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
    },
    getUser: () => JSON.parse(localStorage.getItem('user') || 'null'),
    setUser: (user) => localStorage.setItem('user', JSON.stringify(user)),

    isAuthenticated: () => !!localStorage.getItem('token'),

    // Request Wrapper with Error Handling
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
            
            // Handle Graceful Errors
            if (response.status === 401) {
                API.removeToken();
                if (!window.location.pathname.endsWith('index.html') && window.location.pathname !== '/') {
                    window.location.href = '/index.html';
                }
                throw new Error('Session expired. Please log in again.');
            }
            if (response.status === 403) {
                throw new Error('Forbidden: You do not have permission to perform this action.');
            }

            const contentType = response.headers.get('content-type');
            let data;
            if (contentType && contentType.includes('application/json')) {
                data = await response.json();
            } else {
                data = await response.text();
            }

            if (!response.ok) {
                // Handle 400 Bad Request or 500 Internal Server Error
                throw { 
                    status: response.status, 
                    message: (data && data.message) ? data.message : (typeof data === 'string' ? data : 'An unexpected error occurred') 
                };
            }

            return data;
        } catch (error) {
            console.error(`API Error [${endpoint}]:`, error);
            throw error; // Re-throw to be caught by UI
        }
    },

    // Specific API Methods
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
            if (!user || !user.userId) throw new Error("User ID not found. Please log in again.");
            // Inject createdByUserId required by DTO
            const payload = {
                ...auctionData,
                createdByUserId: user.userId
            };
            return API.request('/api/auctions', {
                method: 'POST',
                body: JSON.stringify(payload)
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

// UI Utilities
const UI = {
    showToast: (message, type = 'info') => {
        const existing = document.getElementById('toast-container');
        if (existing) existing.remove();

        const toast = document.createElement('div');
        toast.id = 'toast-container';
        toast.className = `fixed bottom-4 right-4 px-6 py-4 rounded-xl shadow-2xl transform transition-all duration-300 translate-y-20 opacity-0 z-[9999] text-white font-bold tracking-wide flex items-center gap-3 ${
            type === 'error' ? 'bg-red-600 border border-red-500' : type === 'success' ? 'bg-green-600 border border-green-500' : 'bg-primary border border-primary-dark'
        }`;
        
        let icon = type === 'error' ? '⚠️' : type === 'success' ? '✅' : 'ℹ️';
        toast.innerHTML = `<span class="text-xl">${icon}</span> <span>${message}</span>`;
        
        document.body.appendChild(toast);
        
        // Animate in
        setTimeout(() => {
            toast.classList.remove('translate-y-20', 'opacity-0');
            toast.classList.add('translate-y-0', 'opacity-100');
        }, 100);

        // Remove
        setTimeout(() => {
            toast.classList.remove('translate-y-0', 'opacity-100');
            toast.classList.add('translate-y-20', 'opacity-0');
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    },

    formatCurrency: (amount) => {
        if (!amount && amount !== 0) return '$0.00';
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD'
        }).format(amount);
    },

    formatDate: (dateString) => {
        if (!dateString) return 'N/A';
        return new Date(dateString).toLocaleString([], { 
            month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' 
        });
    },

    getTimeRemaining: (endTime) => {
        if (!endTime) return { total: 0, days: 0, hours: 0, minutes: 0, seconds: 0 };
        const total = Date.parse(endTime) - Date.parse(new Date());
        if (total <= 0) return { total: 0, days: 0, hours: 0, minutes: 0, seconds: 0 };
        const seconds = Math.floor((total / 1000) % 60);
        const minutes = Math.floor((total / 1000 / 60) % 60);
        const hours = Math.floor((total / (1000 * 60 * 60)) % 24);
        const days = Math.floor(total / (1000 * 60 * 60 * 24));
        return { total, days, hours, minutes, seconds };
    }
};
