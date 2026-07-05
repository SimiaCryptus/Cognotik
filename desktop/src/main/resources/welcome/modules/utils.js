// Utility functions module
class Utils {
    static generateSessionId() {
        return Utils.generateSessionIdWithDate(new Date());
    }

    static generateSessionIdWithDate(date) {
        const now = date;
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');
        const randomChars = Math.random().toString(36).substring(2, 6);
        return `U-${year}${month}${day}-${randomChars}`;
    }

    static generateTimestampedDirectory() {
        return Utils.generateTimestampedDirectoryWithDate(new Date());
    }

    static generateTimestampedDirectoryWithDate(date) {
        const now = date;
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');
        const hours = String(now.getHours()).padStart(2, '0');
        const minutes = String(now.getMinutes()).padStart(2, '0');
        const seconds = String(now.getSeconds()).padStart(2, '0');
        return `sessions/${year}${month}${day}${hours}${minutes}${seconds}`;
    }

    static generateCognotikWorkingDir() {
        return Utils.generateCognotikWorkingDirWithDate(new Date(), navigator.platform);
    }

    static generateCognotikWorkingDirWithDate(date, platform) {
        const now = date;
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');
        const hours = String(now.getHours()).padStart(2, '0');
        const minutes = String(now.getMinutes()).padStart(2, '0');
        const seconds = String(now.getSeconds()).padStart(2, '0');
        const timestamp = `${year}${month}${day}-${hours}${minutes}${seconds}`;

        const platformLower = platform.toLowerCase();
        let baseDir;

        if (platformLower.includes('win')) {
            baseDir = '~\\Documents\\Cognotik';
        } else if (platformLower.includes('mac')) {
            baseDir = '~/Documents/Cognotik';
        } else {
            baseDir = '~/Cognotik';
        }

        return `${baseDir}/session-${timestamp}`;
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {Utils};
}