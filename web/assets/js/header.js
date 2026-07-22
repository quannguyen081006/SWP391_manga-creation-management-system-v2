(function () {
    'use strict';

    function closeAllNotificationMenus() {
        var menus = document.querySelectorAll('.noti-actions-menu');
        for (var i = 0; i < menus.length; i++) {
            menus[i].classList.remove('is-open');
        }
    }

    function toggleNotificationMenu(button) {
        var menuId = button.getAttribute('data-menu-id') || ('noti-menu-' + button.getAttribute('data-id'));
        var menu = document.getElementById(menuId);
        if (!menu) {
            return;
        }
        var shouldOpen = !menu.classList.contains('is-open');
        closeAllNotificationMenus();
        if (shouldOpen) {
            menu.classList.add('is-open');
        }
    }

    function deleteNotification(id) {
        closeAllNotificationMenus();
        fetch((window.MANGA_CTX || '') + '/api/v1/notifications/' + id, {
            method: 'DELETE',
            credentials: 'same-origin'
        }).then(function (response) {
            if (response.ok) {
                var items = document.querySelectorAll('[data-noti-id="' + id + '"]');
                for (var i = 0; i < items.length; i++) {
                    items[i].remove();
                }
            }
        });
    }

    function toggleNotificationRead(id, isRead) {
        closeAllNotificationMenus();
        var currentlyRead = isRead === true || isRead === 'true';
        var suffix = currentlyRead ? '/unread' : '/read';
        fetch((window.MANGA_CTX || '') + '/api/v1/notifications/' + id + suffix, {
            method: 'PATCH',
            credentials: 'same-origin'
        }).then(function (response) {
            if (response.ok) {
                window.location.reload();
            }
        });
    }

    function bindNotificationActions() {
        document.addEventListener('click', function (event) {
            var menuButton = event.target.closest ? event.target.closest('[data-notification-menu]') : null;
            if (menuButton) {
                event.preventDefault();
                event.stopPropagation();
                toggleNotificationMenu(menuButton);
                return;
            }

            var deleteButton = event.target.closest ? event.target.closest('[data-notification-delete]') : null;
            if (deleteButton) {
                event.preventDefault();
                event.stopPropagation();
                deleteNotification(deleteButton.getAttribute('data-notification-delete'));
                return;
            }

            var toggleButton = event.target.closest ? event.target.closest('[data-notification-toggle]') : null;
            if (toggleButton) {
                event.preventDefault();
                event.stopPropagation();
                toggleNotificationRead(toggleButton.getAttribute('data-notification-toggle'), toggleButton.getAttribute('data-read'));
                return;
            }

            closeAllNotificationMenus();
        });
    }

    function bindSidebar() {
        var shell = document.querySelector('.app-shell');
        var sidebar = document.querySelector('.side-nav');
        var pinButton = document.querySelector('.sidebar-pin');
        if (!shell || !pinButton) {
            return;
        }

        function setPinned(isPinned) {
            var wasPinned = shell.classList.contains('sidebar-pinned');
            shell.classList.toggle('sidebar-pinned', isPinned);
            shell.classList.toggle('sidebar-hover-suspended', wasPinned && !isPinned);
            pinButton.setAttribute('aria-pressed', isPinned ? 'true' : 'false');
            pinButton.setAttribute('title', 'Collapse sidebar');
            var label = pinButton.querySelector('.pin-label');
            if (label) {
                label.textContent = 'Collapse';
            }
            localStorage.setItem('mangaflow.sidebarPinned', isPinned ? 'true' : 'false');
            pinButton.blur();
        }

        setPinned(localStorage.getItem('mangaflow.sidebarPinned') === 'true');
        pinButton.addEventListener('click', function () {
            setPinned(!shell.classList.contains('sidebar-pinned'));
        });
        if (sidebar) {
            sidebar.addEventListener('mouseleave', function () {
                shell.classList.remove('sidebar-hover-suspended');
            });
        }
    }

    // The sidebar is a fixed-width column with overflow-x: hidden (needed so
    // the collapse/expand animation doesn't show a horizontal scrollbar).
    // Any flyout menu inside it that positions itself with CSS left/right
    // percentages gets silently clipped by that overflow rule -- it "opens"
    // (the DOM/class state changes) but is invisible, which reads as
    // "clicking does nothing". Fix: position flyouts with fixed + JS-computed
    // viewport coordinates so they escape the sidebar's clipping box.
    function positionFlyout(trigger, panel) {
        if (!trigger || !panel) {
            return;
        }
        var rect = trigger.getBoundingClientRect();
        var margin = 12;
        panel.style.position = 'fixed';
        panel.style.left = Math.round(rect.right + margin) + 'px';
        panel.style.right = 'auto';
        panel.style.top = 'auto';
        panel.style.bottom = Math.round(window.innerHeight - rect.bottom) + 'px';
    }

    function bindUserMenu() {
        var trigger = document.getElementById('userMenuTrigger');
        var dropdown = document.getElementById('userDropdown');
        if (!trigger || !dropdown) {
            return;
        }

        function setOpen(isOpen) {
            if (isOpen) {
                positionFlyout(trigger, dropdown);
            }
            dropdown.classList.toggle('open', isOpen);
            trigger.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        }

        function toggleMenu() {
            setOpen(!dropdown.classList.contains('open'));
        }

        trigger.addEventListener('click', function (event) {
            event.preventDefault();
            event.stopPropagation();
            toggleMenu();
        });

        trigger.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                toggleMenu();
                return;
            }
            if (event.key === 'Escape') {
                setOpen(false);
            }
        });

        document.addEventListener('click', function (event) {
            if (!trigger.contains(event.target) && !dropdown.contains(event.target)) {
                setOpen(false);
            }
        });

        window.addEventListener('resize', function () {
            if (dropdown.classList.contains('open')) {
                positionFlyout(trigger, dropdown);
            }
        });
    }

    function bindNotificationFlyout() {
        var details = document.querySelector('.sidebar-account .notify-switcher');
        if (!details) {
            return;
        }
        var summary = details.querySelector('.notify-toggle');
        var panel = details.querySelector('.notify-menu');
        if (!summary || !panel) {
            return;
        }

        details.addEventListener('toggle', function () {
            if (details.open) {
                positionFlyout(summary, panel);
            }
        });

        window.addEventListener('resize', function () {
            if (details.open) {
                positionFlyout(summary, panel);
            }
        });

        // Native <details> already closes on an outside click in most
        // browsers only when focus moves away; be explicit so it behaves
        // like the user-menu dropdown.
        document.addEventListener('click', function (event) {
            if (details.open && !details.contains(event.target)) {
                details.open = false;
            }
        });
    }

    function timeAgo(dateString) {
        if (!dateString) {
            return '';
        }
        var normalized = String(dateString).trim().replace(' ', 'T');
        var date = new Date(normalized);
        if (isNaN(date.getTime())) {
            return dateString;
        }
        var diff = Math.floor((new Date() - date) / 1000);
        if (diff < 60) return 'Just now';
        if (diff < 3600) return Math.floor(diff / 60) + ' minutes ago';
        if (diff < 86400) return Math.floor(diff / 3600) + ' hours ago';
        if (diff < 604800) return Math.floor(diff / 86400) + ' days ago';
        return Math.floor(diff / 604800) + ' weeks ago';
    }

    function renderNotificationTimes() {
        var elements = document.querySelectorAll('.noti-time');
        for (var i = 0; i < elements.length; i++) {
            var raw = elements[i].getAttribute('data-time') || elements[i].textContent.trim();
            elements[i].textContent = timeAgo(raw);
        }
    }

    function initialize() {
        bindSidebar();
        bindUserMenu();
        bindNotificationFlyout();
        bindNotificationActions();
        renderNotificationTimes();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
}());