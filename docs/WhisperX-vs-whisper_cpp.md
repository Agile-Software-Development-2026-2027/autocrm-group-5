**Phoneme-Based ASR**: A paradigm in which speech is transcribed into sequences of symbols representing the phonetic units of a language rather than graphemes, words, or word pieces, in order to obtain a better result.

**Forced alignment**: A technique used to assign a start timestamp to each word: after the text has been transcribed, a second model listens to the audio again and matches each word with the corresponding sound.

**VAD (Voice Activity Detection)**: Detects where human speech is present and where it is not. It is used to discard silence and noise.

**Speaker Diarization**: Splits the audio into segments based on who is speaking and produces an output such as "Speaker 1: ..." "Speaker 2: ...".

---

Both models automatically handle input conversion: _whisperX_ uses the `ffmpeg` utility, while _whisper.cpp_ handles it natively.

The models available are:
- tiny
- base
- small
- medium
- large, large-v2, large-v3

What the two architectures offer:

|                     | WhisperX | whisper.cpp  |
| ------------------- | -------- | ------------ |
| Transcription       | yes      | yes          |
| VAD                 | yes      | yes          |
| Word timestamp      | yes      | limited      |
| Forced alignment    | yes      | experimental |
| Speaker diarization | yes      | no           |

# Benchmark
**RTF**: Indicates how long the model takes to transcribe an audio recording relative to the duration of the audio itself.

**RAM (MB)**: Peak RAM usage in MB while the model is running.

**WER**: Error rate:
- **S**: incorrectly interpreted words
  **reference**: I like drinking coffee
  **transcription**: I like drinking tea
- **D**: words not detected by the model
  **reference**: I like drinking coffe
  **transcription**: I like coffe
- **I**: words that are not present in the speech but that the model transcribed
  **reference**: I like coffe
  **transcription**: I really like coffe

## Local runs using 8-bit quantized models

Runs performed with:
- audio: ES2002a.Mix-Headset.wav
- threads: 4
- repetitions: 3

| tiny        | load (s) | run (s)       | RTF   | RAM (MB) | WER  | errors            |
| ----------- | -------- | ------------- | ----- | -------- | ---- | ----------------- |
| whisperX    | 6.01     | 45.53 +- 1.72 | 0.036 | 1657     | 0.30 | S=256 D=409 I=81  |
| whisper.cpp | 0.04     | 89.15 +- 0.71 | 0.071 | 351      | 0.35 | S= 420 D=359 I=89 |

| base        | load (s) | run (s)       | RTF  | RAM (MB) | WER  | errors            |
| ----------- | -------- | ------------- | ---- | -------- | ---- | ----------------- |
| whisperX    | 4.47     | 66.46 +- 1.01 | 0.05 | 2532     | 0.28 | S=190 D=412 I=85  |
| whisper.cpp | 0.04     | 148 +- 2.61   | 0.11 | 427      | 0.33 | S=294 D=379 I=125 |

| small       | load (s) | run (s)        | RTF  | RAM (MB) | WER  | errors           |
| ----------- | -------- | -------------- | ---- | -------- | ---- | ---------------- |
| whisperX    | 7.17     | 123.44 +- 1.10 | 0.11 | 3311     | 0.25 | S=131 D=420 I=68 |
| whisper.cpp | 0.08     | 390.34 +- 0.71 | 0.30 | 754      | 0.30 | S=197 D=467 I=78 |

| medium      | load (s) | run (s)         | RTF  | RAM (MB) | WER  | errors            |
| ----------- | -------- | --------------- | ---- | -------- | ---- | ----------------- |
| whisperX    | 7.38     | 330.95 +- 0.52  | 0.27 | 6264     | 0.24 | S=111 D=395 I=76  |
| whisper.cpp | 0.16     | 1123.58 +- 6.94 | 0.91 | 1599     | 0.30 | S=142 D=492 I=108 |

| large-v3    | load (s) | run (s)         | RTF  | RAM (MB) | WER  | errors             |
| ----------- | -------- | --------------- | ---- | -------- | ---- | ------------------ |
| whisperX    | 9.69     | 556.64 +- 0.72  | 0.45 | 8457     | 0.23 | S=141 D=378 I=57   |
| whisper.cpp | 0.30     | 2804.73 +- 3.19 | 2.20 | 2761     | 1.55 | S=1977 D=42 I=1781 |

In this case, version v3 of the large model in whisper.cpp hallucinates too much. The documentation states that in these situations it is better to use version v2.

## Runs performed using the GPU on CodeLab with non-quantized models

Runs performed with:
- audio: ES2002a.Mix-Headset.wav
- repetitions: 1

| tiny     | run (s) | WER %  | errors           |
| -------- | ------- | ------ | ---------------- |
| WhisperX | 26.29   | 31.13% | S=253 D=430 I=80 |

| base     | run (s) | WER %  | errors            |
| -------- | ------- | ------ | ----------------- |
| WhisperX | 29.04   | 31.86% | S=234 D=386 I=161 |

| small    | run (s) | WER %  | errors           |
| -------- | ------- | ------ | ---------------- |
| WhisperX | 35.96   | 25.95% | S=134 D=431 I=71 |

| medium   | run (s) | WER %  | errors           |
| -------- | ------- | ------ | ---------------- |
| WhisperX | 55.64   | 22.60% | S=122 D=354 I=78 |

| large    | run (s) | WER %  | errors           |
| -------- | ------- | ------ | ---------------- |
| WhisperX | 75.93   | 23.22% | S=111 D=377 I=81 |

| large-v2 | run (s) | WER %  | errors           |
| -------- | ------- | ------ | ---------------- |
| WhisperX | 79.73   | 25.74% | S=101 D=457 I=73 |

| large-v3 | run (s) | WER %  | errors           |
| -------- | ------- | ------ | ---------------- |
| WhisperX | 73.13   | 23.22% | S=111 D=377 I=81 |

# Conclusions
It is preferable to use the model on a GPU to achieve shorter processing times; therefore, the choice would be WhisperX (whisper.cpp runs only on the CPU).

If a CPU were used, WhisperX would be more suitable because it offers greater accuracy and shorter processing times than whisper.cpp. It also integrates features such as forced alignment and speaker diarization.

As for the choice of model, it comes down to **small** or **medium**:
- **small**: offers lower accuracy (WER = 0.25), but is faster (123.44s) and uses fewer resources (3311 MB)
- **medium**: is more accurate (WER = 0.24), but is slower (330.95s) and uses more resources (6264 MB)
