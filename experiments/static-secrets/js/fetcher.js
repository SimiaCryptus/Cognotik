// Retrieves encrypted blobs as ArrayBuffers.

export class FetchError extends Error {
  constructor(message, { status, cause } = {}) {
    super(message);
    this.name = 'FetchError';
    this.status = status;
    this.cause = cause;
  }
}

export async function fetchBlob(url) {
  let response;
  try {
    response = await fetch(url, {
      // Content blobs are not cached by the app by default.
      cache: 'no-store',
      redirect: 'follow',
    });
  } catch (e) {
    throw new FetchError(`Network error fetching ${url} (CORS or connectivity?)`, { cause: e });
  }
  if (!response.ok) {
    throw new FetchError(`Server returned ${response.status} for ${url}`, {
      status: response.status,
    });
  }
  return response.arrayBuffer();
}
