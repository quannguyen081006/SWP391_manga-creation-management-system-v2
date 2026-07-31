/**
 * Highlights the board decision card whose radio is currently selected.
 *
 * The colours themselves live in styles.css under .board-decision-card.is-selected.
 * That class used to be driven by :has(input:checked), which reads well but is
 * unsupported on Firefox before 121 (and NetBeans' CSS parser flags it as an
 * error), so the class is toggled here instead.
 *
 * The markup puts each radio directly inside its <label class="board-decision-card">,
 * so parentNode is the card - no need for closest(), which keeps this working on
 * older engines too.
 */
(function () {
    'use strict';

    var radios = document.querySelectorAll(
        '.board-decision-card input[type="radio"][name="decision"]');
    if (!radios.length) {
        return;
    }

    function syncSelected() {
        for (var i = 0; i < radios.length; i++) {
            var card = radios[i].parentNode;
            if (!card || !card.className) {
                continue;
            }
            if (radios[i].checked) {
                if (card.className.indexOf('is-selected') === -1) {
                    card.className += ' is-selected';
                }
            } else {
                card.className = card.className
                        .replace(/\s*\bis-selected\b/g, '');
            }
        }
    }

    for (var i = 0; i < radios.length; i++) {
        radios[i].addEventListener('change', syncSelected);
    }

    // The Approve card ships checked, so paint the initial state too.
    syncSelected();
})();
