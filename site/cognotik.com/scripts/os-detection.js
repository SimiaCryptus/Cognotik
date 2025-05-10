/**
 * Detects the user's operating system based on navigator information.
 * 
 * @returns {string} The detected operating system: 'windows', 'mac', 'linux', or 'unknown'
 */
function detectOS() {
    // Get user agent and platform information
    const userAgent = navigator.userAgent.toLowerCase();
    const platform = (navigator.platform || '').toLowerCase();
    // Check for Windows
    if (userAgent.indexOf('win') !== -1 || 
        platform.indexOf('win') !== -1) {
        return 'windows';
    }
    // Check for macOS (includes both "mac" and "darwin" identifiers)
    if (userAgent.indexOf('mac') !== -1 || 
        platform.indexOf('mac') !== -1 ||
        platform.indexOf('darwin') !== -1) {
        return 'mac';
    }
    // Check for Linux (includes various Linux distributions and Android)
    if (userAgent.indexOf('linux') !== -1 || 
        platform.indexOf('linux') !== -1 ||
        userAgent.indexOf('x11') !== -1) {
        // Exclude Android from Linux detection
        if (userAgent.indexOf('android') === -1) {
            return 'linux';
        }
    }
    // If no match found or it's a mobile device, return 'unknown'
    return 'unknown';
}
/**
 * Gets a user-friendly name for the detected operating system.
 * 
 * @param {string} osCode - The OS code returned by detectOS()
 * @returns {string} A user-friendly name for the operating system
 */
function getOSDisplayName(osCode) {
    switch (osCode) {
        case 'windows':
            return 'Windows';
        case 'mac':
            return 'macOS';
        case 'linux':
            return 'Linux';
        default:
            return 'Unknown OS';
    }
}
// Export functions if using ES modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        detectOS,
        getOSDisplayName
    };
}