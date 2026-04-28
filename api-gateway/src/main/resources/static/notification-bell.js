/**
 * notification-bell.js
 * Works with both .nav-right and Tailwind navbar structures.
 * Usage: <script src="/js/notification-bell.js"></script>
 */

(function () {
  const token = localStorage.getItem('token');
  if (!token) return;

  function getUserId() {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.userId;
    } catch { return null; }
  }

  const userId = getUserId();
  if (!userId) return;

  function injectBell() {
    // Support both navbar styles: custom .nav-right OR teammate's Tailwind nav
    const navRight = document.querySelector('.nav-right') ||
                     document.querySelector('nav .flex.items-center.gap-4');
    if (!navRight) return;

    // Don't inject twice
    if (document.getElementById('notification-bell')) return;

    const bell = document.createElement('a');
    bell.href = '/notifications.html';
    bell.id = 'notification-bell';
    bell.title = 'Notifications';
    bell.style.cssText = `
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border-radius: 50%;
      background: #1a1a24;
      border: 1px solid #1e1e2e;
      color: #6b6b7b;
      text-decoration: none;
      font-size: 0.95rem;
      transition: all 0.2s;
      cursor: pointer;
      flex-shrink: 0;
    `;

    bell.innerHTML = `
      🔔
      <span id="nav-unread-count" style="
        display: none;
        position: absolute;
        top: -5px;
        right: -5px;
        background: #f97316;
        color: white;
        font-size: 0.6rem;
        font-weight: 700;
        min-width: 17px;
        height: 17px;
        border-radius: 100px;
        align-items: center;
        justify-content: center;
        padding: 0 3px;
        border: 2px solid #0a0a0f;
        line-height: 1;
      "></span>
    `;

    bell.addEventListener('mouseenter', () => {
      bell.style.borderColor = '#f97316';
      bell.style.color = '#f97316';
    });
    bell.addEventListener('mouseleave', () => {
      bell.style.borderColor = '#1e1e2e';
      bell.style.color = '#6b6b7b';
    });

    // Insert before the sign out button if it exists, otherwise append
    const signOutBtn = navRight.querySelector('button[onclick*="logout"]');
    if (signOutBtn) {
      navRight.insertBefore(bell, signOutBtn);
    } else {
      navRight.appendChild(bell);
    }
  }

  async function fetchUnreadCount() {
    try {
      const res = await fetch(`/api/notifications/user/${userId}/unread-count`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) return;
      const data = await res.json();
      const count = data.count || data.unreadCount || 0;
      updateBell(count);
    } catch (e) {
      // Notification service offline — bell shows without badge
    }
  }

  function updateBell(count) {
    const badge = document.getElementById('nav-unread-count');
    if (!badge) return;
    if (count > 0) {
      badge.textContent = count > 99 ? '99+' : count;
      badge.style.display = 'flex';
    } else {
      badge.style.display = 'none';
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      injectBell();
      fetchUnreadCount();
    });
  } else {
    injectBell();
    fetchUnreadCount();
  }

  setInterval(fetchUnreadCount, 30000);

})();