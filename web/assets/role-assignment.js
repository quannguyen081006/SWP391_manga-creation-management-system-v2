(function () {
    'use strict';

    var SINGLE_ONLY = { MANGAKA: true, ASSISTANT: true, ADMIN: true };
    var DUAL_PAIR = { TANTOU_EDITOR: true, EDITORIAL_BOARD: true };

    function selectedRoles(root) {
        var boxes = root.querySelectorAll('input[name="roles"]:checked');
        var roles = [];
        for (var i = 0; i < boxes.length; i++) {
            roles.push(boxes[i].value);
        }
        return roles;
    }

    function applyRoleRules(root) {
        var roles = selectedRoles(root);
        var hasSingleOnly = roles.some(function (role) { return SINGLE_ONLY[role]; });
        var boxes = root.querySelectorAll('input[name="roles"]');
        for (var i = 0; i < boxes.length; i++) {
            var box = boxes[i];
            var role = box.value;
            var checked = box.checked;
            var disable = false;
            if (hasSingleOnly) {
                disable = !checked;
            } else if (!checked && SINGLE_ONLY[role] && roles.length > 0) {
                disable = true;
            } else if (roles.length >= 2 && !checked) {
                disable = true;
            } else if (roles.length === 1 && !DUAL_PAIR[role] && !checked) {
                disable = true;
            }
            box.disabled = disable;
        }
    }

    function bindRoleForms() {
        var forms = document.querySelectorAll('.role-choice-grid, .role-check-grid');
        for (var i = 0; i < forms.length; i++) {
            (function (root) {
                root.addEventListener('change', function () {
                    applyRoleRules(root);
                });
                applyRoleRules(root);
            })(forms[i]);
        }
    }

    document.addEventListener('DOMContentLoaded', bindRoleForms);
})();
