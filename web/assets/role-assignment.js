(function () {
  'use strict';

  var SINGLE_ONLY = { MANGAKA: true, ASSISTANT: true };
  var EDITOR_PAIR = { TANTOU_EDITOR: true, EDITORIAL_BOARD: true };

  /**
   * Finds a role checkbox inside one role grid.
   *
   * @param {Element} root role grid container
   * @param {string} role role value to find
   * @return {Element|null} matching checkbox, or null when absent
   */
  function findRole(root, role) {
    return root.querySelector('input[name="roles"][value="' + role + '"]');
  }

  /**
   * Enforces BR-SYS role-combination rules in the browser before submit.
   *
   * @param {Element} root role grid container
   * @param {Element|null} changed checkbox that triggered the change
   * @return {void}
   */
  function enforceRoleRules(root, changed) {
    var boxes = root.querySelectorAll('input[name="roles"]');

    if (!changed) {
      // On initial load, re-apply single-role rules to any prechecked value.
      for (var k = 0; k < boxes.length; k++) {
        if (boxes[k].checked && SINGLE_ONLY[boxes[k].value]) {
          enforceRoleRules(root, boxes[k]);
          return;
        }
      }
    }

    if (!changed || !changed.checked) {
      return;
    }

    if (SINGLE_ONLY[changed.value]) {
      // MANGAKA and ASSISTANT are single-role accounts.
      for (var j = 0; j < boxes.length; j++) {
        if (boxes[j] !== changed) {
          boxes[j].checked = false;
        }
      }
      return;
    }

    if (EDITOR_PAIR[changed.value]) {
      // TANTOU_EDITOR + EDITORIAL_BOARD is the only valid dual-role pair.
      var mangaka = findRole(root, 'MANGAKA');
      var assistant = findRole(root, 'ASSISTANT');
      if (mangaka) {
        mangaka.checked = false;
      }
      if (assistant) {
        assistant.checked = false;
      }
    }
  }

  /**
   * Binds role validation to checkbox-based role assignment grids.
   *
   * @return {void}
   */
  function bindRoleForms() {
    var forms = document.querySelectorAll('.role-check-grid');
    if (!forms || forms.length === 0) {
      return;
    }
    for (var i = 0; i < forms.length; i++) {
      (function (root) {
        if (!root) {
          return;
        }
        root.addEventListener('change', function (event) {
          if (event.target && event.target.matches('input[name="roles"]')) {
            enforceRoleRules(root, event.target);
          }
        });
        enforceRoleRules(root, null);
      })(forms[i]);
    }
  }

  document.addEventListener('DOMContentLoaded', bindRoleForms);
})();
