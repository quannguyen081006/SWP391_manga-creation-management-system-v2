(function () {
    'use strict';

    var input = document.getElementById('avatar');
    var preview = document.getElementById('avatarPreview');
    var empty = document.getElementById('avatarEmpty');
    if (!input || !preview || !empty) {
        return;
    }

    input.addEventListener('change', function () {
        var file = input.files && input.files[0];
        if (!file) {
            return;
        }
        var extension = file.name.split('.').pop().toLowerCase();
        if (['jpg', 'jpeg', 'png'].indexOf(extension) === -1 || file.size > 2 * 1024 * 1024) {
            input.value = '';
            window.alert('Avatar must be a JPG, JPEG, or PNG file no larger than 2MB.');
            return;
        }
        preview.src = URL.createObjectURL(file);
        preview.classList.remove('is-hidden');
        empty.classList.add('is-hidden');
    });
}());
