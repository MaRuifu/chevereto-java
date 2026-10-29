// chevereto-java 前端交互
// 上传（本地上传 tab + URL tab）、嵌入代码复制、NSFW 揭示、专辑密码显隐
(function () {
    var dropzone = document.getElementById('dropzone');
    var options = {
        privacy: document.getElementById('opt-privacy'),
        category: document.getElementById('opt-category'),
        nsfw: document.getElementById('opt-nsfw')
    };

    // ---- 删图链接 ----
    // 游客没有账号，"删除这张图"的唯一凭据就是上传时返回的一次性链接（口令明文只在这一次出现，
    // 库里存的是 bcrypt 哈希）。所以必须在结果里当场显示，并额外存一份到 sessionStorage，
    // 这样点"详情页"跳过去还能再看到（同一浏览器会话内）。
    function absUrl(u) {
        if (!u) return '';
        return /^https?:\/\//i.test(u) ? u : location.origin + (u.charAt(0) === '/' ? u : '/' + u);
    }

    function rememberDeleteUrl(image) {
        if (!image || !image.delete_url) return;
        try {
            sessionStorage.setItem('chv_del_' + image.id_encoded, absUrl(image.delete_url));
        } catch (e) { /* 隐私模式下 sessionStorage 可能不可用，忽略 */ }
    }

    function deleteHintHtml(image) {
        if (!image || !image.delete_url) return '';
        return '<div class="delete-hint">' +
            '<div class="dh-title"><i class="fa-solid fa-key"></i> 删图链接（只显示这一次）</div>' +
            '<p class="dh-note">复制保存好：凭这条链接任何人都能删除这张图片（无需登录），关掉页面后就再也拿不到了。</p>' +
            '<div class="dh-row">' +
            '<input class="mono" type="text" readonly value="' + escapeAttr(absUrl(image.delete_url)) + '" onclick="this.select()">' +
            '<button type="button" class="btn btn-ghost" onclick="chvCopyDelete(this)"><i class="fa-solid fa-copy"></i> 复制</button>' +
            '</div>' +
            '</div>';
    }

    window.chvCopyDelete = function (btn) {
        var box = btn && btn.parentNode ? btn.parentNode.querySelector('input') : null;
        if (!box) return;
        box.focus();
        box.select();
        var original = btn.innerHTML;
        var done = function () {
            btn.innerHTML = '<i class="fa-solid fa-check"></i> 已复制';
            setTimeout(function () { btn.innerHTML = original; }, 1600);
        };
        // http 页面不是 secure context，navigator.clipboard 用不了，要退回 execCommand
        if (navigator.clipboard && window.isSecureContext) {
            navigator.clipboard.writeText(box.value).then(done, function () {
                try { document.execCommand('copy'); done(); } catch (e) { /* 复制失败就手动全选 */ }
            });
        } else {
            try { document.execCommand('copy'); done(); } catch (e) { /* 同上 */ }
        }
    };

    function withOptions(form) {
        if (options.privacy) form.append('privacy', options.privacy.value);
        if (options.category) form.append('category', options.category.value);
        if (options.nsfw && options.nsfw.checked) form.append('nsfw', 'true');
        return form;
    }

    // ---- 本地上传 ----
    if (dropzone) {
        var fileInput = document.getElementById('file-input');
        var pick = document.getElementById('pick');
        var progressWrap = document.getElementById('upload-progress');
        var progressBar = document.getElementById('progress-bar');
        var progressText = document.getElementById('progress-text');
        var results = document.getElementById('upload-results');
        var maxMb = (window.__UPLOAD__ && window.__UPLOAD__.maxMb) || 20;

        pick.addEventListener('click', function (e) { e.preventDefault(); fileInput.click(); });
        dropzone.addEventListener('click', function (e) { if (e.target !== pick) fileInput.click(); });
        ['dragenter', 'dragover'].forEach(function (ev) {
            dropzone.addEventListener(ev, function (e) { e.preventDefault(); dropzone.classList.add('dragover'); });
        });
        ['dragleave', 'drop'].forEach(function (ev) {
            dropzone.addEventListener(ev, function (e) { e.preventDefault(); dropzone.classList.remove('dragover'); });
        });
        dropzone.addEventListener('drop', function (e) { if (e.dataTransfer.files.length) uploadFiles(e.dataTransfer.files); });
        fileInput.addEventListener('change', function () {
            if (fileInput.files.length) { uploadFiles(fileInput.files); fileInput.value = ''; }
        });

        function uploadFiles(fileList) {
            var files = Array.prototype.slice.call(fileList);
            progressWrap.hidden = false;
            results.innerHTML = '';
            var done = 0;
            files.forEach(function (file) {
                if (file.size > maxMb * 1024 * 1024) {
                    addResult(file, null, '文件超过 ' + maxMb + ' MB 限制');
                    done++;
                    return;
                }
                var form = withOptions(new FormData());
                form.append('file', file);
                form.append('json', 'true');
                var xhr = new XMLHttpRequest();
                xhr.open('POST', '/upload');
                xhr.responseType = 'json';
                xhr.upload.onprogress = function (e) {
                    if (e.lengthComputable) {
                        var pct = Math.round(((done + e.loaded / e.total) / files.length) * 100);
                        progressBar.style.width = pct + '%';
                        progressText.textContent = '上传中 ' + pct + '%';
                    }
                };
                xhr.onload = function () {
                    done++;
                    var resp = xhr.response || {};
                    if (resp.status_code === 200 && resp.image) addResult(file, resp.image, null);
                    else addResult(file, null, (resp.error && resp.error.message) || '上传失败');
                    if (done === files.length) progressText.textContent = '完成';
                };
                xhr.onerror = function () { done++; addResult(file, null, '网络错误'); };
                xhr.send(form);
            });
        }

        function addResult(file, image, error) {
            var item = document.createElement('div');
            item.className = 'result-item';
            if (image) {
                rememberDeleteUrl(image);
                item.innerHTML =
                    '<img src="' + image.thumb.url + '">' +
                    '<div style="flex:1;overflow:hidden"><div style="font-weight:600">' + escapeHtml(image.title || file.name) + '</div>' +
                    '<a href="' + image.url + '" target="_blank">直链</a> · ' +
                    '<a href="/image/' + image.id_encoded + '" target="_blank">详情页</a></div>' +
                    deleteHintHtml(image);
            } else {
                item.innerHTML =
                    '<div style="flex:1"><span class="r-fail">✗ ' + escapeHtml(file.name) + '</span>' +
                    '<span class="muted"> — ' + escapeHtml(error || '上传失败') + '</span></div>';
            }
            results.appendChild(item);
        }
    }

    // ---- URL 上传 ----
    var urlBtn = document.getElementById('url-upload-btn');
    if (urlBtn) {
        urlBtn.addEventListener('click', function () {
            var input = document.getElementById('url-input');
            if (!input.value) return;
            var form = withOptions(new FormData());
            form.append('source', input.value);
            form.append('json', 'true');
            urlBtn.disabled = true;
            fetch('/upload', { method: 'POST', body: form })
                .then(function (r) { return r.json(); })
                .then(function (resp) {
                    urlBtn.disabled = false;
                    var results = document.getElementById('upload-results');
                    var item = document.createElement('div');
                    item.className = 'result-item';
                    if (resp.status_code === 200 && resp.image) {
                        rememberDeleteUrl(resp.image);
                        item.innerHTML = '<img src="' + resp.image.thumb.url + '">' +
                            '<div style="flex:1"><div style="font-weight:600">' + escapeHtml(resp.image.title || resp.image.filename) + '</div>' +
                            '<a href="' + resp.image.url + '" target="_blank">直链</a> · ' +
                            '<a href="/image/' + resp.image.id_encoded + '" target="_blank">详情页</a></div>' +
                            deleteHintHtml(resp.image);
                    } else {
                        item.innerHTML = '<span class="r-fail">✗ ' + escapeHtml((resp.error && resp.error.message) || '上传失败') + '</span>';
                    }
                    results.appendChild(item);
                    input.value = '';
                });
        });
    }

    // ---- 上传 tab 切换 ----
    document.querySelectorAll('.upload-tab[data-tab]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            document.querySelectorAll('.upload-tab[data-tab]').forEach(function (b) { b.classList.remove('active'); });
            btn.classList.add('active');
            document.getElementById('panel-local').hidden = btn.dataset.tab !== 'local';
            document.getElementById('panel-url').hidden = btn.dataset.tab !== 'url';
        });
    });

    // ---- 嵌入代码（查看器）----
    // DOMContentLoaded 触发，确保模板内联的 window.__IMAGE__ 已就绪
    document.addEventListener('DOMContentLoaded', function () {
        var embedCode = document.getElementById('embed-code');
        if (!embedCode || !window.__IMAGE__) return;
        var img = window.__IMAGE__;
        var codes = {
            'html-full': '<img src="' + img.url + '" alt="' + escapeAttr(img.title) + '" />',
            'html-thumb': '<a href="' + img.url + '"><img src="' + img.thumbUrl + '" alt="' + escapeAttr(img.title) + '" /></a>',
            'markdown': '[![' + escapeAttr(img.title) + '](' + img.thumbUrl + ')](' + img.url + ')',
            'bbcode': '[img]' + img.url + '[/img]',
            'direct': img.url
        };
        function fillEmbed(key) { embedCode.value = codes[key] || ''; }
        fillEmbed('html-full');
        document.querySelectorAll('#embed-tabs .tab').forEach(function (btn) {
            btn.addEventListener('click', function () {
                document.querySelectorAll('#embed-tabs .tab').forEach(function (b) { b.classList.remove('active'); });
                btn.classList.add('active');
                fillEmbed(btn.dataset.embed);
            });
        });
        window.copyEmbed = function () { navigator.clipboard.writeText(embedCode.value); };
    });

    // ---- NSFW 揭示 ----
    window.revealNsfw = function () {
        var viewer = document.querySelector('.viewer-canvas');
        var cover = document.querySelector('.nsfw-cover');
        if (viewer) viewer.classList.remove('nsfw');
        if (cover) cover.remove();
    };

    // ---- 专辑密码显隐 ----
    var privacySel = document.getElementById('album-privacy');
    if (privacySel) {
        window.toggleAlbumPwd = function () {
            document.getElementById('album-pwd-label').hidden = privacySel.value !== 'password';
        };
    }

    function escapeHtml(s) {
        var div = document.createElement('div');
        div.textContent = s == null ? '' : String(s);
        return div.innerHTML;
    }
    function escapeAttr(s) { return escapeHtml(s).replace(/"/g, '&quot;'); }
})();
