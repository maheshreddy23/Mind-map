package com.maheshreddy.mym.core.models

// GENERATED from the Hugging Face API (file sizes, SHA-256 / Git hashes, pinned revisions, licences) on 3 Nov 2026.
// Every download is pinned to [CatalogModel.revision], so the hashes below stay valid even if a repo changes.

internal object CatalogData {
    val models: List<CatalogModel> = listOf(
        CatalogModel(
            id = "whisper-tiny-en", category = ModelCategory.SPEECH, name = "Whisper tiny (English)",
            repo = "ggerganov/whisper.cpp", revision = "5359861c739e955e79d9a303bcbc70fb988958b1",
            files = listOf(RemoteFile("ggml-tiny.en.bin", 77704715L, "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f")),
            licence = Licence("MIT", "https://opensource.org/license/mit", openSource = true), activeParamsB = 0.04, quality = 1, languages = "English", note = "Fastest; misses technical terms more often.",
        ),
        CatalogModel(
            id = "whisper-base-en", category = ModelCategory.SPEECH, name = "Whisper base (English)",
            repo = "ggerganov/whisper.cpp", revision = "5359861c739e955e79d9a303bcbc70fb988958b1",
            files = listOf(RemoteFile("ggml-base.en.bin", 147964211L, "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002")),
            licence = Licence("MIT", "https://opensource.org/license/mit", openSource = true), activeParamsB = 0.07, quality = 2, languages = "English", note = "Fast and light.",
        ),
        CatalogModel(
            id = "whisper-small-en", category = ModelCategory.SPEECH, name = "Whisper small (English)",
            repo = "ggerganov/whisper.cpp", revision = "5359861c739e955e79d9a303bcbc70fb988958b1",
            files = listOf(RemoteFile("ggml-small.en.bin", 487614201L, "c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d")),
            licence = Licence("MIT", "https://opensource.org/license/mit", openSource = true), activeParamsB = 0.24, quality = 3, languages = "English", note = "Best balance for English; 0.48x real time measured on a OnePlus 13R.",
        ),
        CatalogModel(
            id = "whisper-medium-en-q5", category = ModelCategory.SPEECH, name = "Whisper medium (English, Q5)",
            repo = "ggerganov/whisper.cpp", revision = "5359861c739e955e79d9a303bcbc70fb988958b1",
            files = listOf(RemoteFile("ggml-medium.en-q5_0.bin", 539225533L, "76733e26ad8fe1c7a5bf7531a9d41917b2adc0f20f2e4f5531688a8c6cd88eb0")),
            licence = Licence("MIT", "https://opensource.org/license/mit", openSource = true), activeParamsB = 0.77, quality = 4, languages = "English", note = "Most accurate English option; slower.",
        ),
        CatalogModel(
            id = "whisper-tiny", category = ModelCategory.SPEECH, name = "Whisper tiny (multilingual)",
            repo = "ggerganov/whisper.cpp", revision = "5359861c739e955e79d9a303bcbc70fb988958b1",
            files = listOf(RemoteFile("ggml-tiny.bin", 77691713L, "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21")),
            licence = Licence("MIT", "https://opensource.org/license/mit", openSource = true), activeParamsB = 0.04, quality = 1, languages = "Multilingual", note = "Fastest multilingual.",
        ),
        CatalogModel(
            id = "whisper-base", category = ModelCategory.SPEECH, name = "Whisper base (multilingual)",
            repo = "ggerganov/whisper.cpp", revision = "5359861c739e955e79d9a303bcbc70fb988958b1",
            files = listOf(RemoteFile("ggml-base.bin", 147951465L, "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe")),
            licence = Licence("MIT", "https://opensource.org/license/mit", openSource = true), activeParamsB = 0.07, quality = 2, languages = "Multilingual", note = "",
        ),
        CatalogModel(
            id = "whisper-small", category = ModelCategory.SPEECH, name = "Whisper small (multilingual)",
            repo = "ggerganov/whisper.cpp", revision = "5359861c739e955e79d9a303bcbc70fb988958b1",
            files = listOf(RemoteFile("ggml-small.bin", 487601967L, "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b")),
            licence = Licence("MIT", "https://opensource.org/license/mit", openSource = true), activeParamsB = 0.24, quality = 3, languages = "Multilingual", note = "",
        ),
        CatalogModel(
            id = "whisper-large-v3-turbo-q5", category = ModelCategory.SPEECH, name = "Whisper large-v3 turbo (multilingual, Q5)",
            repo = "ggerganov/whisper.cpp", revision = "5359861c739e955e79d9a303bcbc70fb988958b1",
            files = listOf(RemoteFile("ggml-large-v3-turbo-q5_0.bin", 574041195L, "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2")),
            licence = Licence("MIT", "https://opensource.org/license/mit", openSource = true), activeParamsB = 0.81, quality = 5, languages = "Multilingual", note = "Best multilingual accuracy; heaviest.",
        ),
        CatalogModel(
            id = "silero-vad-5.1.2", category = ModelCategory.VAD, name = "Silero voice detection v5.1.2",
            repo = "ggml-org/whisper-vad", revision = "9ffd54a1e1ee413ddf265af9913beaf518d1639b",
            files = listOf(RemoteFile("ggml-silero-v5.1.2.bin", 885098L, "29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf")),
            licence = Licence("MIT", "https://opensource.org/license/mit", openSource = true), activeParamsB = 0.0, quality = 3, languages = "Any", note = "Detects when you start and stop speaking (continuous mode).",
        ),
        CatalogModel(
            id = "qwen3-4b-instruct-2507", category = ModelCategory.LLM, name = "Qwen3 4B Instruct 2507",
            repo = "unsloth/Qwen3-4B-Instruct-2507-GGUF", revision = "a06e946bb6b655725eafa393f4a9745d460374c9",
            files = listOf(RemoteFile("Qwen3-4B-Instruct-2507-Q4_K_M.gguf", 2497281120L, "3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597")),
            licence = Licence("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0", openSource = true), activeParamsB = 4.0, quality = 4, languages = "Multilingual", note = "Strong, direct answers (no thinking mode). Best default on 8 GB+ phones.",
        ),
        CatalogModel(
            id = "qwen3.5-4b", category = ModelCategory.LLM, name = "Qwen3.5 4B",
            repo = "unsloth/Qwen3.5-4B-GGUF", revision = "e87f176479d0855a907a41277aca2f8ee7a09523",
            files = listOf(RemoteFile("Qwen3.5-4B-Q4_K_M.gguf", 2740937888L, "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4")),
            licence = Licence("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0", openSource = true), activeParamsB = 4.0, quality = 5, languages = "Multilingual", note = "Newest Qwen; highest quality here, a little slower.",
        ),
        CatalogModel(
            id = "qwen3.5-2b", category = ModelCategory.LLM, name = "Qwen3.5 2B",
            repo = "unsloth/Qwen3.5-2B-GGUF", revision = "f6d5376be1edb4d416d56da11e5397a961aca8ae",
            files = listOf(RemoteFile("Qwen3.5-2B-Q4_K_M.gguf", 1280835840L, "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223")),
            licence = Licence("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0", openSource = true), activeParamsB = 2.0, quality = 3, languages = "Multilingual", note = "Good quality for 6 GB phones.",
        ),
        CatalogModel(
            id = "qwen2.5-1.5b", category = ModelCategory.LLM, name = "Qwen2.5 1.5B Instruct",
            repo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF", revision = "91cad51170dc346986eccefdc2dd33a9da36ead9",
            files = listOf(RemoteFile("qwen2.5-1.5b-instruct-q4_k_m.gguf", 1117320736L, "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e")),
            licence = Licence("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0", openSource = true), activeParamsB = 1.5, quality = 2, languages = "Multilingual", note = "Small and quick; simpler questions.",
        ),
        CatalogModel(
            id = "qwen2.5-3b", category = ModelCategory.LLM, name = "Qwen2.5 3B Instruct",
            repo = "Qwen/Qwen2.5-3B-Instruct-GGUF", revision = "7dabda4d13d513e3e842b20f0d435c732f172cbe",
            files = listOf(RemoteFile("qwen2.5-3b-instruct-q4_k_m.gguf", 2104932768L, "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d")),
            licence = Licence("Qwen Research License (non-commercial)", "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct/blob/main/LICENSE", openSource = false), activeParamsB = 3.0, quality = 3, languages = "Multilingual", note = "Fresher Notes' original model. Research licence: personal, non-commercial use only.",
        ),
        CatalogModel(
            id = "gemma4-e2b", category = ModelCategory.LLM, name = "Gemma 4 E2B",
            repo = "unsloth/gemma-4-E2B-it-GGUF", revision = "0314792d7f1f7e229411f620751375812bb9faf2",
            files = listOf(RemoteFile("gemma-4-E2B-it-Q4_K_M.gguf", 3106738272L, "740185b21d22ceb83a11c3aa62ad5842ef32c70f6096d756bbee85a1e4ec34b8")),
            licence = Licence("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0", openSource = true), activeParamsB = 2.0, quality = 4, languages = "Multilingual", note = "Runs like a 2B model; large file (per-layer embeddings).",
        ),
        CatalogModel(
            id = "gemma4-e4b", category = ModelCategory.LLM, name = "Gemma 4 E4B",
            repo = "unsloth/gemma-4-E4B-it-GGUF", revision = "bfc15c382204943c3a8fff0c750b94ae2364d7a3",
            files = listOf(RemoteFile("gemma-4-E4B-it-Q4_K_M.gguf", 4977171584L, "85a896a047553e842f25297ee5b031d64ff30147d9c4af17b1e4b394cd1fab87")),
            licence = Licence("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0", openSource = true), activeParamsB = 4.0, quality = 5, languages = "Multilingual", note = "Very capable; needs 12 GB RAM.",
        ),
        CatalogModel(
            id = "gemma3-1b", category = ModelCategory.LLM, name = "Gemma 3 1B",
            repo = "ggml-org/gemma-3-1b-it-GGUF", revision = "f9c28bcd85737ffc5aef028638d3341d49869c27",
            files = listOf(RemoteFile("gemma-3-1b-it-Q4_K_M.gguf", 806058240L, "8ccc5cd1f1b3602548715ae25a66ed73fd5dc68a210412eea643eb20eb75a135")),
            licence = Licence("Gemma Terms of Use", "https://ai.google.dev/gemma/terms", openSource = false), activeParamsB = 1.0, quality = 1, languages = "Multilingual", note = "Smallest; for low-memory phones.",
        ),
        CatalogModel(
            id = "gemma3-4b", category = ModelCategory.LLM, name = "Gemma 3 4B",
            repo = "ggml-org/gemma-3-4b-it-GGUF", revision = "d0976223747697cb51e056d85c532013931fe52e",
            files = listOf(RemoteFile("gemma-3-4b-it-Q4_K_M.gguf", 2489757856L, "882e8d2db44dc554fb0ea5077cb7e4bc49e7342a1f0da57901c0802ea21a0863")),
            licence = Licence("Gemma Terms of Use", "https://ai.google.dev/gemma/terms", openSource = false), activeParamsB = 4.0, quality = 4, languages = "Multilingual", note = "",
        ),
        CatalogModel(
            id = "llama3.2-3b", category = ModelCategory.LLM, name = "Llama 3.2 3B Instruct",
            repo = "bartowski/Llama-3.2-3B-Instruct-GGUF", revision = "5ab33fa94d1d04e903623ae72c95d1696f09f9e8",
            files = listOf(RemoteFile("Llama-3.2-3B-Instruct-Q4_K_M.gguf", 2019377696L, "6c1a2b41161032677be168d354123594c0e6e67d2b9227c84f296ad037c728ff")),
            licence = Licence("Llama 3.2 Community License", "https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct/blob/main/LICENSE.txt", openSource = false), activeParamsB = 3.0, quality = 3, languages = "English+", note = "",
        ),
        CatalogModel(
            id = "phi4-mini", category = ModelCategory.LLM, name = "Phi-4-mini Instruct",
            repo = "bartowski/microsoft_Phi-4-mini-instruct-GGUF", revision = "7ff82c2aaa4dde30121698a973765f39be5288c0",
            files = listOf(RemoteFile("microsoft_Phi-4-mini-instruct-Q4_K_M.gguf", 2491874688L, "01999f17c39cc3074afae5e9c539bc82d45f2dd7faa3917c66cbef76fce8c0c2")),
            licence = Licence("MIT", "https://opensource.org/license/mit", openSource = true), activeParamsB = 3.8, quality = 4, languages = "English+", note = "Strong reasoning for its size.",
        ),
        CatalogModel(
            id = "bge-small-en", category = ModelCategory.EMBEDDING, name = "BGE small (English)",
            repo = "CompendiumLabs/bge-small-en-v1.5-gguf", revision = "d32f8c040ea3b516330eeb75b72bcc2d3a780ab7",
            files = listOf(RemoteFile("bge-small-en-v1.5-q8_0.gguf", 36806944L, "ec38e8da142596baa913124ae50550de284b6916bf59577ef2f0cb9660c2f514")),
            licence = Licence("MIT", "https://opensource.org/license/mit", openSource = true), activeParamsB = 0.03, quality = 3, languages = "English", note = "Tiny and fast; good for English notes.", embeddingDim = 384,
        ),
        CatalogModel(
            id = "nomic-embed-v1.5", category = ModelCategory.EMBEDDING, name = "Nomic Embed v1.5",
            repo = "nomic-ai/nomic-embed-text-v1.5-GGUF", revision = "0188c9bf409793f810680a5a431e7b899c46104c",
            files = listOf(RemoteFile("nomic-embed-text-v1.5.Q8_0.gguf", 146146432L, "3e24342164b3d94991ba9692fdc0dd08e3fd7362e0aacc396a9a5c54a544c3b7")),
            licence = Licence("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0", openSource = true), activeParamsB = 0.14, quality = 4, languages = "English", note = "Better matches for longer notes.", embeddingDim = 768,
        ),
        CatalogModel(
            id = "qwen3-embedding-0.6b", category = ModelCategory.EMBEDDING, name = "Qwen3 Embedding 0.6B",
            repo = "Qwen/Qwen3-Embedding-0.6B-GGUF", revision = "370f27d7550e0def9b39c1f16d3fbaa13aa67728",
            files = listOf(RemoteFile("Qwen3-Embedding-0.6B-Q8_0.gguf", 639150592L, "06507c7b42688469c4e7298b0a1e16deff06caf291cf0a5b278c308249c3e439")),
            licence = Licence("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0", openSource = true), activeParamsB = 0.6, quality = 5, languages = "Multilingual", note = "Best quality and multilingual; larger.", embeddingDim = 1024,
        ),
        CatalogModel(
            id = "embeddinggemma-300m", category = ModelCategory.EMBEDDING, name = "EmbeddingGemma 300M",
            repo = "ggml-org/embeddinggemma-300M-GGUF", revision = "0f741b5a6585bd53aeb15cd1372c56f2a0f65e12",
            files = listOf(RemoteFile("embeddinggemma-300M-Q8_0.gguf", 333590944L, "b5ce9d77a3fc4b3b39ccb5643c36777911cc4eb46a66962eadfa3f5f60490d63")),
            licence = Licence("Gemma Terms of Use", "https://ai.google.dev/gemma/terms", openSource = false), activeParamsB = 0.3, quality = 4, languages = "Multilingual", note = "", embeddingDim = 768,
        ),
        CatalogModel(
            id = "piper-en_GB-alan-medium", category = ModelCategory.VOICE, name = "Alan (British male)",
            repo = "csukuangfj/vits-piper-en_GB-alan-medium", revision = "a449faf03a5e8e2eed012793ce95441d0738b605",
            files = listOf(
                RemoteFile("en_GB-alan-medium.onnx", 63201430L, "d907c48857000940104a9ad3248a94617df917d1a525c9495490fdbf87fd54b2"),
                RemoteFile("tokens.txt", 921L, "82449830b618abdb4d2f1a2de38db6c322d906d4", HashKind.GIT_SHA1),
            ),
            licence = Licence("Mimic 3 voice licence (see link)", "https://github.com/MycroftAI/mimic3-voices", openSource = false), activeParamsB = 0.0, quality = 3, languages = "English", note = "Piper voice (sherpa-onnx).",
            sharedData = SharedData.ESPEAK_NG,
        ),
        CatalogModel(
            id = "piper-en_GB-jenny_dioco-medium", category = ModelCategory.VOICE, name = "Jenny (British female)",
            repo = "csukuangfj/vits-piper-en_GB-jenny_dioco-medium", revision = "00bac9d2e32e93b1e60237d26b85971bba95092d",
            files = listOf(
                RemoteFile("en_GB-jenny_dioco-medium.onnx", 63201430L, "bd5207a2752d8766a8b771437776fd7575031a9f9876eaf13b8db778b150993a"),
                RemoteFile("tokens.txt", 921L, "82449830b618abdb4d2f1a2de38db6c322d906d4", HashKind.GIT_SHA1),
            ),
            licence = Licence("Jenny dataset licence (see link)", "https://github.com/dioco-group/jenny-tts-dataset", openSource = false), activeParamsB = 0.0, quality = 3, languages = "English", note = "Piper voice (sherpa-onnx).",
            sharedData = SharedData.ESPEAK_NG,
        ),
        CatalogModel(
            id = "piper-en_GB-northern_english_male-medium", category = ModelCategory.VOICE, name = "Northern English male",
            repo = "csukuangfj/vits-piper-en_GB-northern_english_male-medium", revision = "1d9a616e1cdeb91d8bc0b921a8b31296e2c9ff75",
            files = listOf(
                RemoteFile("en_GB-northern_english_male-medium.onnx", 63201430L, "d23e7891af7062eb188283dba94866e25ffd5b01a0d9fb9a23c71a39b75b2308"),
                RemoteFile("tokens.txt", 907L, "faf0ab137b386bdfbe74f0b4f1a4c9d839d893f4", HashKind.GIT_SHA1),
            ),
            licence = Licence("CC BY-SA 4.0 (voice dataset)", "http://www.openslr.org/83/", openSource = true), activeParamsB = 0.0, quality = 3, languages = "English", note = "Piper voice (sherpa-onnx).",
            sharedData = SharedData.ESPEAK_NG,
        ),
        CatalogModel(
            id = "piper-en_US-amy-medium", category = ModelCategory.VOICE, name = "Amy (American female)",
            repo = "csukuangfj/vits-piper-en_US-amy-medium", revision = "79936839cc9409b1a752a45c629fe21639e1c1b2",
            files = listOf(
                RemoteFile("en_US-amy-medium.onnx", 63201425L, "fbaa8e36d8f26fe6f3ebb65cab461e629d8b37a5b7c5fb78fb64317db73e1c25"),
                RemoteFile("tokens.txt", 921L, "82449830b618abdb4d2f1a2de38db6c322d906d4", HashKind.GIT_SHA1),
            ),
            licence = Licence("Mimic 3 voice licence (see link)", "https://github.com/MycroftAI/mimic3-voices", openSource = false), activeParamsB = 0.0, quality = 3, languages = "English", note = "Piper voice (sherpa-onnx).",
            sharedData = SharedData.ESPEAK_NG,
        ),
        CatalogModel(
            id = "piper-en_US-lessac-medium", category = ModelCategory.VOICE, name = "Lessac (American female)",
            repo = "csukuangfj/vits-piper-en_US-lessac-medium", revision = "83f7470750b36549037551ef7007fa3b4b8697e0",
            files = listOf(
                RemoteFile("en_US-lessac-medium.onnx", 63201425L, "ea793a648ce0370665f99fbf891eae3b3a973565c72b08534bb5b2c04e8f2332"),
                RemoteFile("tokens.txt", 921L, "82449830b618abdb4d2f1a2de38db6c322d906d4", HashKind.GIT_SHA1),
            ),
            licence = Licence("Blizzard 2013 Lessac licence (voice dataset)", "https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html", openSource = false), activeParamsB = 0.0, quality = 3, languages = "English", note = "Piper voice (sherpa-onnx).",
            sharedData = SharedData.ESPEAK_NG,
        ),
        CatalogModel(
            id = "piper-en_US-ryan-medium", category = ModelCategory.VOICE, name = "Ryan (American male)",
            repo = "csukuangfj/vits-piper-en_US-ryan-medium", revision = "2784cf0f7510312ed567a5d227bcbec28dcd661e",
            files = listOf(
                RemoteFile("en_US-ryan-medium.onnx", 63201425L, "8a4063318faeda9bf67f89d25b29cc4bd5fde8832422aa451a5d5d2647553081"),
                RemoteFile("tokens.txt", 921L, "82449830b618abdb4d2f1a2de38db6c322d906d4", HashKind.GIT_SHA1),
            ),
            licence = Licence("CC BY-NC-SA 4.0 (voice dataset, non-commercial)", "https://www.kaggle.com/datasets/roholazandie/ryanspeech", openSource = false), activeParamsB = 0.0, quality = 3, languages = "English", note = "Piper voice (sherpa-onnx).",
            sharedData = SharedData.ESPEAK_NG,
        ),
        CatalogModel(
            id = "piper-en_US-amy-low", category = ModelCategory.VOICE, name = "Amy, low quality (small, 16 kHz)",
            repo = "csukuangfj/vits-piper-en_US-amy-low", revision = "76da6ca287517a49f58b943c4c4fdb0c1e94d61f",
            files = listOf(
                RemoteFile("en_US-amy-low.onnx", 63104657L, "8275b02c37c4ce6483b26a91704807e6de0c3f0bf8068e5d3b3598ab0da4253e"),
                RemoteFile("tokens.txt", 763L, "112a2083f80b8eee7af6ca02e18f036d03ab1495", HashKind.GIT_SHA1),
            ),
            licence = Licence("Mimic 3 voice licence (see link)", "https://github.com/MycroftAI/mimic3-voices", openSource = false), activeParamsB = 0.0, quality = 2, languages = "English", note = "Piper voice (sherpa-onnx).",
            sharedData = SharedData.ESPEAK_NG,
        ),
        CatalogModel(
            id = "piper-en_US-hfc_female-medium", category = ModelCategory.VOICE, name = "HFC female (American)",
            repo = "csukuangfj/vits-piper-en_US-hfc_female-medium", revision = "127829225a73a7f87cb0f07be5aef40895b87abd",
            files = listOf(
                RemoteFile("en_US-hfc_female-medium.onnx", 63201425L, "6d8b3711715f17f29b9f0ded97571924ead9a06e300bfdf3680b014a51ddc9e5"),
                RemoteFile("tokens.txt", 954L, "c4aa7526c5ad10fc5dcb6a85f665e183d49375d3", HashKind.GIT_SHA1),
            ),
            licence = Licence("CC BY-NC-SA 4.0 (voice dataset, non-commercial)", "https://ast-astrec.nict.go.jp/en/release/hi-fi-captain/", openSource = false), activeParamsB = 0.0, quality = 3, languages = "English", note = "Piper voice (sherpa-onnx).",
            sharedData = SharedData.ESPEAK_NG,
        ),
    )

    /** espeak-ng-data shared by every Piper voice (manifest: assets/catalog/espeak-ng-data.tsv). */
    const val ESPEAK_REPO = "csukuangfj/vits-piper-en_GB-alan-medium"
    const val ESPEAK_REVISION = "a449faf03a5e8e2eed012793ce95441d0738b605"
}
