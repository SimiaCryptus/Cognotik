/**
 * Update the download UI: detect OS, fetch latest release, set button/link
 */
function updateDownloadUI() {
    const osText = document.getElementById('detected-os');
    const versionText = document.getElementById('latest-version');
    const downloadBtn = document.getElementById('download-button');
    const downloadInfoSimple = document.getElementById('download-info-simple'); // For aria-live updates
    const githubReleasesUrl = 'https://github.com/SimiaCryptus/Cognotik/releases';
    const osNames = { windows: 'Windows', mac: 'macOS', linux: 'Linux', unknown: 'Unknown' };

    // Initial state
    // Set initial text and add aria-busy for screen readers
    if (osText) {
        osText.textContent = `Detected OS: Checking...`;
        osText.setAttribute('aria-busy', 'true');
    }
    if (versionText) {
        versionText.textContent = `Latest Version: Checking...`;
        versionText.setAttribute('aria-busy', 'true');
    }
    if (downloadBtn) {
        downloadBtn.textContent = 'Checking for updates...';
        downloadBtn.disabled = true;
        downloadBtn.href = '#';
        downloadBtn.setAttribute('aria-label', 'Checking for updates, please wait.');
        downloadBtn.setAttribute('aria-busy', 'true');
    }
    // Announce the initial checking state
    if (downloadInfoSimple) downloadInfoSimple.setAttribute('aria-live', 'polite');

    // Detect OS
    const os = typeof detectOS === 'function' ? detectOS() : 'unknown';
    if (osText) {
        osText.textContent = `Detected OS: ${osNames[os] || 'Unknown'}`;
        osText.removeAttribute('aria-busy'); // Done checking OS
    }

    // If unknown, fallback
    if (os === 'unknown') {
        if (downloadBtn) {
            downloadBtn.textContent = 'View All Releases on GitHub';
            downloadBtn.href = githubReleasesUrl;
            downloadBtn.disabled = false;
            downloadBtn.setAttribute('aria-label', 'View all Cognotik releases on GitHub');
            downloadBtn.removeAttribute('aria-busy');
        }
        if (versionText) {
            versionText.textContent = 'Could not detect your operating system';
            versionText.removeAttribute('aria-busy');
        }
        return;
    }

    // Fetch latest release
    if (typeof fetchLatestRelease !== 'function') {
        // fallback if API not loaded
        if (downloadBtn) {
            downloadBtn.textContent = 'View All Releases on GitHub';
            downloadBtn.href = githubReleasesUrl;
            downloadBtn.disabled = false;
            downloadBtn.setAttribute('aria-label', 'View all Cognotik releases on GitHub');
            downloadBtn.removeAttribute('aria-busy');
        }
        if (versionText) {
            versionText.textContent = 'Release information unavailable';
            versionText.removeAttribute('aria-busy');
        }
        return;
    }

    fetchLatestRelease().then(release => {
        if (!release) throw new Error('No releases found');
        const version = release.tag_name ? release.tag_name.replace(/^v/, '') : 'Unknown';
        if (versionText) {
            versionText.textContent = `Latest Version: ${version}`;
            versionText.removeAttribute('aria-busy'); // Done checking version
        }

        // Find asset
        let asset = typeof findDownloadAsset === 'function' ? findDownloadAsset(release, os) : null;
        if (asset) {
            if (downloadBtn) {
                const buttonText = `Download for ${osNames[os]} v${version}`;
                downloadBtn.textContent = buttonText;
                downloadBtn.href = asset.browser_download_url;
                downloadBtn.disabled = false;
                downloadBtn.setAttribute('download', asset.name);
                downloadBtn.setAttribute('aria-label', `${buttonText} (${asset.name})`);
                downloadBtn.removeAttribute('aria-busy');
            }
        } else {
            if (downloadBtn) {
                downloadBtn.textContent = 'View All Releases on GitHub';
                downloadBtn.href = githubReleasesUrl;
                downloadBtn.disabled = false;
                downloadBtn.setAttribute('aria-label', 'View all Cognotik releases on GitHub');
                downloadBtn.removeAttribute('aria-busy');
            }
        }
    }).catch(err => {
        if (downloadBtn) {
            downloadBtn.textContent = 'Could not fetch releases. Visit GitHub.';
            downloadBtn.href = githubReleasesUrl;
            downloadBtn.disabled = false;
            downloadBtn.setAttribute('aria-label', 'Could not fetch release information. Visit GitHub to view releases.');
            downloadBtn.removeAttribute('aria-busy');
        }
        if (versionText) {
            versionText.textContent = 'Release information unavailable';
            versionText.removeAttribute('aria-busy');
        }
        console.error('Error fetching release info:', err);
    });
}

// Auto-initialize on DOMContentLoaded
document.addEventListener('DOMContentLoaded', updateDownloadUI);