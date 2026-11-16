const maxSizeMb = 100;

export const AUDIO_FILE_PICKER_CONFIG = {
  maxSizeMb,
  maxSizeBytes: maxSizeMb * 1024 * 1024,
  genericMimeTypes: ['application/octet-stream', 'binary/octet-stream'],
  // Eccezioni MIME per contenitori che possono includere audio, senza vincoli sul nome.
  containerMimeTypes: ['application/ogg', 'application/mp4', 'video/mp4', 'video/webm'],
} as const;
