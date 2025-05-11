/**
 * GitHub API integration for Cognotik website
 * Handles fetching release information and finding appropriate download assets
 */

// Cache constants
const CACHE_KEY = 'cognotik_release_data';
const CACHE_EXPIRY = 3600000; // 1 hour in milliseconds (Consider localStorage for longer persistence)

/**
 * Fetches the latest release information from GitHub
 * Uses caching to avoid excessive API calls
 * @returns {Promise<Object|null>} The latest release object or null on error
 */
async function fetchLatestRelease() {
  try {
    // Check cache first
    const cachedData = checkCache(CACHE_KEY, CACHE_EXPIRY);
    if (cachedData) {
      console.log('Using cached release data');
      return cachedData;
    }

    // If no cache, fetch from GitHub API
    console.log('Fetching latest release from GitHub API');
    
    // Try the /latest endpoint first
    let response = await fetch('https://api.github.com/repos/SimiaCryptus/Cognotik/releases/latest');
    
    // If that fails, try the /releases endpoint and take the first item
    if (!response.ok) {
      console.log('Latest endpoint failed, trying releases list');
      response = await fetch('https://api.github.com/repos/SimiaCryptus/Cognotik/releases');
      

      if (!response.ok) {
        throw new Error(`GitHub API error: ${response.status}`);
      }
      
      const releases = await response.json();
      if (!releases || releases.length === 0) {
        throw new Error('No releases found');
      }
      
      // Cache the first release from the list
      cacheData(CACHE_KEY, releases[0]);
      return releases[0];
    }
    
    const latestRelease = await response.json();
    
    // Cache the data
    cacheData(CACHE_KEY, latestRelease);
    return latestRelease;
  } catch (error) {
    console.error('Error fetching release data:', error);
    return null;
  }
}

/**
 * Finds the appropriate download asset for the user's OS
 * @param {Object} release - The release object from GitHub API
 * @param {string} os - The detected OS ('windows', 'mac', 'linux', 'unknown')
 * @returns {Object|null} The matching asset object or null if no match
 */
function findDownloadAsset(release, os) {
  if (!release || !release.assets || release.assets.length === 0) {
    return null;
  }

  // Define file extensions and priorities by OS
  const osExtensions = {
    windows: ['.msi', '.exe', '.zip'],
    mac: ['.dmg', '.pkg', '.zip'],
    linux: ['.deb', '.rpm', '.AppImage', '.tar.gz', '.zip']
  };

  // If OS is unknown or not in our mapping, return null
  if (!os || !osExtensions[os]) {
    return null;
  }

  // Get the extensions for the detected OS
  const extensions = osExtensions[os];
  
  // First try to find assets with the preferred extensions in order
  for (const ext of extensions) {
    const asset = release.assets.find(asset => 
      asset.name.toLowerCase().endsWith(ext) && 
      asset.browser_download_url && 
      asset.state === 'uploaded'
    );
    
    if (asset) {
      return asset;
    }
  }

  // If no specific match, look for any asset that might contain the OS name
  const osKeywords = {
    windows: ['win', 'windows'],
    mac: ['mac', 'macos', 'osx'],
    linux: ['linux', 'ubuntu', 'debian', 'fedora', 'centos']
  };

  const keywords = osKeywords[os];
  for (const keyword of keywords) {
    const asset = release.assets.find(asset => 
      asset.name.toLowerCase().includes(keyword) && 
      asset.browser_download_url && 
      asset.state === 'uploaded'
    );
    
    if (asset) {
      return asset;
    }
  }

  // No matching asset found
  return null;
}

/**
 * Checks if there is valid cached release data
 * @param {string} key - The cache key (e.g., CACHE_KEY)
 * @param {number} expiry - The cache expiry time in milliseconds (e.g., CACHE_EXPIRY)
 * @returns {Object|null} The cached release data or null if no valid cache
 */
function checkCache(key, expiry) {
  try {
    const cachedItem = localStorage.getItem(key); // Use localStorage for persistence across sessions
    if (!cachedItem) {
      return null;
    }

    const { timestamp, data } = JSON.parse(cachedItem);
    const now = new Date().getTime();
    
    // Check if cache is expired
    if (now - timestamp > expiry) {
      console.log('Cache expired');
      localStorage.removeItem(key);
      return null;
    }
    
    return data;
  } catch (error) {
    console.error('Error reading from cache:', error);
    return null;
  }
}

/**
 * Caches the release data with a timestamp
 * @param {string} key - The cache key (e.g., CACHE_KEY)
 * @param {Object} data - The release data to cache
 */
function cacheData(key, data) {
  try {
    const cacheItem = {
      timestamp: new Date().getTime(),
      data: data
    };
    
    localStorage.setItem(key, JSON.stringify(cacheItem)); // Use localStorage
    console.log('Release data cached');
  } catch (error) {
    console.error('Error caching release data:', error);
    // Continue without caching - non-critical error
  }
}

/**
 * Gets the GitHub releases page URL
 * @returns {string} The URL to the GitHub releases page
 */
function getGitHubReleasesUrl() {
  return 'https://github.com/SimiaCryptus/Cognotik/releases';
}