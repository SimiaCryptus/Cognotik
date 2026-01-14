// GitHub API Connector for jQueryFileTree
const $ = window.jQuery;

let repoTree = null;
let treePromise = null;

function getTree() {
    if (repoTree) return Promise.resolve(repoTree);
    if (treePromise) return treePromise;
    treePromise = fetch('https://api.github.com/repos/SimiaCryptus/Cognotik/git/trees/main?recursive=1')
        .then(r => r.json())
        .then(data => {
            repoTree = data.tree || [];
            return repoTree;
        });
    return treePromise;
}

// Intercept AJAX to serve GitHub API data
if ($ && !$.ajax.isGithubConnectorPatched) {
    const originalAjax = $.ajax;
    $.ajax = function(options) {
        if (options.url === 'github_connector') {
            const deferred = $.Deferred();
            let dir = options.data.dir;
            
            // Normalize dir
            if (!dir) dir = '';
            dir = decodeURIComponent(dir);
            
            getTree().then(tree => {
                // Ensure trailing slash for directory matching
                if (dir && !dir.endsWith('/')) dir += '/';
                
                // Filter items that are direct children of dir
                const items = tree.filter(item => {
                    if (!item.path.startsWith(dir)) return false;
                    const rel = item.path.slice(dir.length);
                    return rel.length > 0 && rel.indexOf('/') === -1;
                });

                // Sort directories first
                items.sort((a, b) => {
                    const aIsDir = (a.type === 'tree');
                    const bIsDir = (b.type === 'tree');
                    if (aIsDir && !bIsDir) return -1;
                    if (!aIsDir && bIsDir) return 1;
                    return a.path.localeCompare(b.path);
                });

                let html = '<ul class="jqueryFileTree" style="display: none;">';
                items.forEach(item => {
                    const name = item.path.split('/').pop();
                    const isDir = (item.type === 'tree');
                    const rel = item.path + (isDir ? '/' : '');
                    const ext = name.split('.').pop();
                    const cls = isDir ? 'directory collapsed' : 'file ext_' + ext;
                    html += `<li class="${cls}"><a href="javascript:void(0)" rel="${rel}">${name}</a></li>`;
                });
                html += '</ul>';
                
                if (options.success) options.success(html);
                deferred.resolve(html);
            }).catch(e => {
                console.error("Tree fetch failed", e);
                deferred.reject(e);
            });

            return deferred.promise();
        }
        return originalAjax.apply(this, arguments);
    };
    $.ajax.isGithubConnectorPatched = true;
}

export function initFileTree(selector, rootPath) {
    if (!$) return;
    
    $(document).ready(function() {
        // Start prefetching immediately
        getTree();

        // Ensure rootPath ends with /
        if (rootPath && !rootPath.endsWith('/')) rootPath += '/';

        $(selector).fileTree({
            root: rootPath,
            script: 'github_connector',
            expandSpeed: 100,
            collapseSpeed: 100
        }, function(file) {
            window.open('https://github.com/SimiaCryptus/Cognotik/blob/main/' + file, '_blank');
        });
    });
}