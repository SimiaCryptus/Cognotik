/**
 * Update the download UI: detect OS, fetch latest release, set button/link
 */
function updateDownloadUI() {
    const osText = document.getElementById('detected-os');
    const versionText = document.getElementById('latest-version');
    const downloadBtn = document.getElementById('download-button');
    const githubReleasesUrl = 'https://github.com/SimiaCryptus/Cognotik/releases';
    const osNames = { windows: 'Windows', mac: 'macOS', linux: 'Linux', unknown: 'Unknown' };

    // Initial state
    if (osText) osText.textContent = `Detected OS: Checking...`;
    if (versionText) versionText.textContent = `Latest Version: Checking...`;
    if (downloadBtn) {
        downloadBtn.textContent = 'Checking for updates...';
        downloadBtn.disabled = true;
        downloadBtn.href = '#';
    }

    // Detect OS
    const os = typeof detectOS === 'function' ? detectOS() : 'unknown';
    if (osText) osText.textContent = `Detected OS: ${osNames[os] || 'Unknown'}`;

    // If unknown, fallback
    if (os === 'unknown') {
        if (downloadBtn) {
            downloadBtn.textContent = 'View All Releases on GitHub';
            downloadBtn.href = githubReleasesUrl;
            downloadBtn.disabled = false;
        }
        if (versionText) versionText.textContent = 'Could not detect your operating system';
        return;
    }

    // Fetch latest release
    if (typeof fetchLatestRelease !== 'function') {
        // fallback if API not loaded
        if (downloadBtn) {
            downloadBtn.textContent = 'View All Releases on GitHub';
            downloadBtn.href = githubReleasesUrl;
            downloadBtn.disabled = false;
        }
        if (versionText) versionText.textContent = 'Release information unavailable';
        return;
    }

    fetchLatestRelease().then(release => {
        if (!release) throw new Error('No releases found');
        const version = release.tag_name ? release.tag_name.replace(/^v/, '') : 'Unknown';
        if (versionText) versionText.textContent = `Latest Version: ${version}`;

        // Find asset
        let asset = typeof findDownloadAsset === 'function' ? findDownloadAsset(release, os) : null;
        if (asset) {
            if (downloadBtn) {
                downloadBtn.textContent = `Download for ${osNames[os]} v${version}`;
                downloadBtn.href = asset.browser_download_url;
                downloadBtn.disabled = false;
                downloadBtn.setAttribute('download', asset.name);
            }
        } else {
            if (downloadBtn) {
                downloadBtn.textContent = 'View All Releases on GitHub';
                downloadBtn.href = githubReleasesUrl;
                downloadBtn.disabled = false;
            }
        }
    }).catch(err => {
        if (downloadBtn) {
            downloadBtn.textContent = 'Could not fetch releases. Visit GitHub.';
            downloadBtn.href = githubReleasesUrl;
            downloadBtn.disabled = false;
        }
        if (versionText) versionText.textContent = 'Release information unavailable';
        console.error('Error fetching release info:', err);
    });
}

// Auto-initialize on DOMContentLoaded
document.addEventListener('DOMContentLoaded', updateDownloadUI);