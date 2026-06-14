(function () {
    'use strict';

    var decision = document.querySelector('select[name="decision"]');
    var reviewNote = document.getElementById('reviewNote');
    if (decision && reviewNote) {
        var syncReviewNote = function () {
            reviewNote.required = decision.value === 'REJECT' || decision.value === 'REVISE';
        };
        decision.addEventListener('change', syncReviewNote);
        syncReviewNote();
    }

    var boardDecisions = document.querySelectorAll('form[action$="/board-vote"] input[name="decision"]');
    var boardNote = document.getElementById('boardVoteNote');
    var boardHint = document.getElementById('boardVoteHint');
    var boardCards = document.querySelectorAll('.board-decision-card');
    if (boardDecisions.length && boardNote && boardHint) {
        var syncBoardNote = function () {
            var selected = document.querySelector('form[action$="/board-vote"] input[name="decision"]:checked');
            var needsNote = selected && (selected.value === 'REVISE' || selected.value === 'REJECT');
            boardNote.required = needsNote;
            boardHint.textContent = needsNote ? 'Required for this decision.' : 'Optional for approval. Required for revision or rejection.';
            for (var i = 0; i < boardCards.length; i++) boardCards[i].classList.remove('is-selected');
            if (selected) selected.closest('.board-decision-card').classList.add('is-selected');
        };
        for (var i = 0; i < boardDecisions.length; i++) boardDecisions[i].addEventListener('change', syncBoardNote);
        syncBoardNote();
    }

    document.addEventListener('click', function (event) {
        var button = event.target.closest ? event.target.closest('[data-reject-reason]') : null;
        if (button) window.alert(button.getAttribute('data-reject-reason') || 'No rejection reason was provided.');
    });
}());
