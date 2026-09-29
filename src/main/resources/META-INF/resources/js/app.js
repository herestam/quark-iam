/* =====================================================================
   Progressive enhancement only: every page works with JavaScript disabled.
   The script handles flash dismissal, destructive-action confirmation, the
   campaign recipient picker, and live job progress polling.
   ===================================================================== */
(function () {
    'use strict';

    // ------------------------------------------------------- flash banner
    document.addEventListener('click', function (event) {
        var dismiss = event.target.closest('[data-dismiss-flash]');
        if (dismiss) {
            var banner = dismiss.closest('.flash');
            if (banner) {
                banner.remove();
            }
        }
    });

    // ------------------------------------------- confirm destructive posts
    document.addEventListener('submit', function (event) {
        var message = event.target.getAttribute('data-confirm');
        if (message && !window.confirm(message)) {
            event.preventDefault();
        }
    });

    // ------------------------------------------------- recipient picker
    function initPicker() {
        var list = document.querySelector('[data-recipient-list]');
        var hidden = document.querySelector('[data-recipient-ids]');
        if (!list || !hidden) {
            return;
        }
        var counter = document.querySelector('[data-selection-count]');
        var boxes = Array.prototype.slice.call(list.querySelectorAll('[data-recipient-checkbox]'));
        var filter = document.querySelector('[data-recipient-filter]');

        function selectedIds() {
            return boxes
                .filter(function (box) { return box.checked; })
                .map(function (box) { return box.value; });
        }

        function sync() {
            hidden.value = selectedIds().join(',');
            if (counter) {
                counter.textContent = selectedIds().length + ' selected';
            }
        }

        list.addEventListener('change', sync);

        var selectAll = document.querySelector('[data-select-all]');
        if (selectAll) {
            selectAll.addEventListener('click', function () {
                boxes.forEach(function (box) {
                    if (!box.closest('li').hidden) {
                        box.checked = true;
                    }
                });
                sync();
            });
        }

        var selectNone = document.querySelector('[data-select-none]');
        if (selectNone) {
            selectNone.addEventListener('click', function () {
                boxes.forEach(function (box) { box.checked = false; });
                sync();
            });
        }

        if (filter) {
            filter.addEventListener('input', function () {
                var needle = filter.value.trim().toLowerCase();
                Array.prototype.forEach.call(list.children, function (item) {
                    var haystack = (item.getAttribute('data-filter-text') || '').toLowerCase();
                    item.hidden = needle !== '' && haystack.indexOf(needle) === -1;
                });
            });
        }

        sync();
    }

    // ------------------------------------------------------ live job data
    var TERMINAL = { COMPLETED: true, CANCELLED: true, FAILED: true };

    function fetchJob(id) {
        return fetch('/api/jobs/' + id, {headers: {'Accept': 'application/json'}})
            .then(function (response) { return response.ok ? response.json() : null; })
            .then(function (body) { return body && body.data ? body.data : null; })
            .catch(function () { return null; });
    }

    function setField(row, field, value) {
        var cell = row.querySelector('[data-field="' + field + '"]');
        if (cell) {
            cell.textContent = value;
        }
    }

    function paintProgress(root, job) {
        var wrap = root.querySelector('[data-job-progress="' + job.id + '"]');
        if (!wrap) {
            return;
        }
        var bar = wrap.querySelector('.bar');
        if (bar) {
            bar.style.width = job.progressPercent + '%';
            bar.className = 'bar' + (job.status === 'FAILED' ? ' bar-failed'
                : job.status === 'CANCELLED' ? ' bar-cancelled' : '');
        }
        var label = wrap.querySelector('.progress-label');
        if (label) {
            label.textContent = job.progressPercent + '% \u00b7 '
                + job.processedCount + '/' + job.totalCount;
        }
    }

    function initPolling(container) {
        var interval = parseInt(container.getAttribute('data-poll-interval'), 10) || 0;
        if (!interval) {
            return;
        }
        var hint = document.querySelector('[data-poll-hint]');
        var rows = Array.prototype.slice.call(container.querySelectorAll('tr[data-job-id]'));
        var ids = rows.map(function (row) { return row.getAttribute('data-job-id'); });
        if (ids.length === 0) {
            return;
        }

        var busy = false;
        function poll() {
            if (busy || document.hidden) {
                return;
            }
            busy = true;
            Promise.all(ids.map(fetchJob)).then(function (jobs) {
                var active = 0;
                jobs.forEach(function (job, index) {
                    if (!job) {
                        return;
                    }
                    if (!TERMINAL[job.status]) {
                        active += 1;
                    }
                    var row = rows[index];
                    setField(row, 'successCount', job.successCount);
                    setField(row, 'failedCount', job.failedCount);
                    setField(row, 'totalCount', job.totalCount);
                    setField(row, 'pendingCount', job.pendingCount);

                    var status = row.querySelector('[data-field="status"]');
                    if (status) {
                        status.textContent = job.status;
                        status.className = status.className.replace(/badge-[a-z]+/, badgeClass(job.status));
                    }
                    paintProgress(row, job);
                });
                if (hint) {
                    hint.hidden = false;
                }
                // Nothing left to watch: stop asking the server for free.
                if (active === 0) {
                    clearInterval(timer);
                }
            }).finally(function () {
                busy = false;
            });
        }

        var timer = setInterval(poll, interval);
        poll();
    }

    function badgeClass(status) {
        switch (status) {
            case 'RUNNING': return 'badge-running';
            case 'PAUSED': return 'badge-paused';
            case 'COMPLETED': return 'badge-completed';
            case 'CANCELLED': return 'badge-cancelled';
            case 'FAILED': return 'badge-failed';
            default: return 'badge-pending';
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        initPicker();
        Array.prototype.forEach.call(document.querySelectorAll('[data-poll-interval]'), initPolling);
    });
})();
