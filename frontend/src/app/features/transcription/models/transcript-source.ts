/** Dati sorgente immutabili; id identifica la trascrizione, non una sua revisione. */
export interface TranscriptSource {
  readonly id: string;
  readonly text: string;
}
