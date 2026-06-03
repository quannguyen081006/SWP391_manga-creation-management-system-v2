(function () {
    'use strict';

    var SINGLE_ONLY = { MANGAKA: true, ASSISTANT: true };
    var EDITOR_PAIR = { TANTOU_EDITOR: true, EDITORIAL_BOARD: true };

    function findRole(root, role) {
        return root.querySelector('input[name="roles"][value="' + role + '"]');
    }

    function enforceRoleRules(root, changed) {
        var isCreateUserForm = root.classList.contains('role-choice-grid');
        var boxes = root.querySelectorAll('input[name="roles"]');
        for (var i = 0; i < boxes.length; i++) {
            if (isCreateUserForm && boxes[i].value === 'ADMIN') {
                boxes[i].checked = false;
                boxes[i].disabled = true;
            } else {
                boxes[i].disabled = false;
            }
        }

        if (!changed) {
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
            for (var j = 0; j < boxes.length; j++) {
                if (boxes[j] !== changed) {
                    boxes[j].checked = false;
                }
            }
            return;
        }

        if (EDITOR_PAIR[changed.value]) {
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

    function bindRoleForms() {
        var forms = document.querySelectorAll('.role-choice-grid, .role-check-grid');
        for (var i = 0; i < forms.length; i++) {
            (function (root) {
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
