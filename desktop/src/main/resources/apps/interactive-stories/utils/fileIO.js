/**
  * File I/O utilities
  */

/**
  * Read a file from the session storage
  * @param {string} basePath - Base path for the session
  * @param {string} filePath - Path to the file
  * @returns {Promise<string|null>} File content or null if not found
  */
export async function readFile(basePath, filePath) {
        const resp = await fetch(basePath + '/' + filePath);
        if (!resp.ok) {
            if (resp.status === 404 || resp.status === 400) return null;
            throw new Error(`Failed to read ${filePath}: ${resp.status}`);
        }
        return await resp.text();
  }

/**
  * Write content to a file in the session storage
  * @param {string} basePath - Base path for the session
  * @param {string} filePath - Path to the file
  * @param {string} content - Content to write
  * @returns {Promise<boolean>} True if successful
  */
export async function writeFile(basePath, filePath, content) {
        const resp = await fetch(basePath + '/' + filePath, {
            method: 'PUT',
            headers: { 'Content-Type': 'text/plain; charset=utf-8' },
            body: content
        });
        if (!resp.ok) {
            throw new Error(`Failed to write ${filePath}: ${resp.status}`);
        }
        return true;
  }

/**
  * Check if a file exists
  * @param {string} basePath - Base path for the session
  * @param {string} filePath - Path to the file
  * @returns {Promise<boolean>} True if file exists
  */
export async function fileExists(basePath, filePath) {
        try {
            const resp = await fetch(basePath + '/' + filePath, { method: 'HEAD' });
            return resp.ok;
        } catch (e) {
            return false;
        }
  }

/**
  * List files in a directory
  * @param {string} basePath - Base path for the session
  * @param {string} dirPath - Path to the directory
  * @returns {Promise<Array>} Array of file entries
  */
export async function listFiles(basePath, dirPath) {
        const url = basePath + '/' + dirPath + '/_files.json';
        try {
            const resp = await fetch(url);
            if (!resp.ok) return [];
            const data = await resp.json();
            return data.entries || [];
        } catch (e) {
            return [];
        }
  }

/**
  * Delete a file
  * @param {string} basePath - Base path for the session
  * @param {string} filePath - Path to the file
  * @returns {Promise<boolean>} True if successful
  */
export async function deleteFile(basePath, filePath) {
        const resp = await fetch(basePath + '/' + filePath, {
            method: 'DELETE'
        });
        return resp.ok || resp.status === 404;
  }

export const FileIOUtils = {
     readFile,
     writeFile,
     fileExists,
     listFiles,
     deleteFile
};