# Listening voices

Generated with Piper (https://github.com/rhasspy/piper) voices from https://huggingface.co/rhasspy/piper-voices.

## en_GB-alan-medium

# Model card for alan (medium)

* Language: en_GB (English, Great Britain)
* Speakers: 1
* Quality: medium
* Samplerate: 22,050Hz

## Dataset

* URL: https://github.com/MycroftAI/mimic3-voices/blob/master/voices/en_UK/apope_low
* License: See URL

## Training

Finetuned from U.S. English lessac voice (medium quality).

## en_GB-cori-medium

# Model card for cori (medium)

* Language: en_GB (English, Great Britain)
* Speakers: 1
* Quality: medium
* Samplerate: 22,050Hz

## Dataset

* URL: https://www.openslr.org/109/
* License: CC BY 4.0 (Hi-Fi Multi-Speaker English TTS Dataset, NVIDIA Corporation)
* Paper: Bakhturina, Lavrukhin, Ginsburg and Zhang, "Hi-Fi Multi-Speaker English TTS Dataset", Interspeech 2021, https://arxiv.org/abs/2104.01497

The voice of Cori Samuel, a volunteer reader for LibriVox (https://librivox.org),
whose public-domain audiobook recordings Hi-Fi TTS is built from (reader 92). This
voice uses her recordings from the clean subset: 27.3 hours, resampled from 44.1kHz,
with adjacent segments merged up to 12 seconds (35,146 -> 13,574 utterances).
Thank you, Cori.

## Training

Finetuned for 150 epochs from the hifi_tts_92 base model (medium quality), which was
trained on the same recordings, now phonemized as British English (`en-gb-x-rp`)
instead of U.S. English. With MRD and SDP.

This succeeds Bryce Beattie's earlier cori voice (`cori-med-640.ckpt`, trained from
scratch on her LibriVox recordings; see https://brycebeattie.com/files/tts/).

Requires Piper 1.5 or higher.

## en_GB-jenny_dioco-medium

# Model card for jenny_dioco (medium)

* Language: en_GB (English, Great Britain)
* Speakers: 1
* Quality: medium
* Samplerate: 22,050Hz

## Dataset

* URL: https://github.com/dioco-group/jenny-tts-dataset
* License: See URL

## Training

Finetuned from the U.S. English hifi_tts_92 base model (medium quality; the voice of Cori Samuel, Hi-Fi TTS reader 92), with MRD and SDP.

Default inference settings are noise_scale 0.5 and noise_w 0.333.

Requires Piper 1.5 or higher.

## en_GB-northern_english_male-medium

# Model card for northern_english_male (medium)

* Language: en_GB (English, Great Britain)
* Speakers: 1
* Quality: medium
* Samplerate: 22,050Hz

## Dataset

* URL: http://www.openslr.org/83/
* License: CC-BY-SA 4.0 International

## Training

Finetuned from the rdh base model (medium quality; nl_BE, one Flemish speaker, CC0), with MRD and SDP.

Default inference settings are noise_scale 0.5 and noise_w 0.333.

Requires Piper 1.5 or higher.

