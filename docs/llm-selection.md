# LLM selection for action-item extraction

Which model reads a sales-call transcript (text, from Fireflies or WhisperX) and extracts the action items that we push
to HubSpot. Models, limits, prices and benchmark results were checked on **2026-11-23**; this market changes monthly, so
re-check the sources at the bottom before relying on a number.

**Our constraints.** Development and the demo must cost €0, and nobody in the team wants to register a credit card.
The company that will use the system has few calls, so volume is low. Calls last up to ~90 minutes, i.e. roughly
15k–25k tokens of transcript (our estimate). The output must be valid JSON that maps onto HubSpot Tasks. The flow is
asynchronous, so speed is secondary.

The report compares two ways of getting a model:

- **A. Hosted models usable for free without a credit card** (Gemini, Mistral, Cohere): we call the provider's API.
- **B. Powerful open models on a server**: we download the model and run it on a machine we control.

## 1. The candidates

### 1.1 A — Hosted models without a credit card

We only considered providers that give an API key **without a credit card** and simply **stop** (HTTP 429) when the free
quota is used up, so nothing can ever be charged. Three providers qualify today:

| | Google Gemini API | Mistral API ("Free mode") | Cohere API (trial key) |
|---|---|---|---|
| **Model** | **Gemini 3.5 Flash-Lite** (`gemini-3.5-flash-lite`) | **Mistral Medium 3.5** (`mistral-medium-latest`) | **Command A+** (`command-a-plus-05-2026`) |
| Sign-up | Google account, key from AI Studio | account + phone verification | account |
| Free limits | 10 requests/min, 250k tokens/min, **500 requests/day** per project | 1 request/second, 500k tokens/min | 20 requests/min, **1,000 calls/month** per account |
| Context window | 1M tokens | 256k tokens | 128k tokens |
| JSON schema enforced | yes | yes | yes |
| Data on the free tier | in the EEA, Google applies its paid-service data terms to the free quota | **may be used to train Mistral's models**; can be switched off in the admin panel | de-identified inputs may be used in limited cases, depending on user controls |
| **Allowed use** | development and testing | evaluation and prototyping | **trial keys may not be used for production or commercial purposes** |

Notes:

- Excluded because they ask for a card or prepaid credit: **OpenAI**, **Anthropic** (Claude), **xAI** (Grok). **GitHub
  Models** is free but caps each request at 8,000 input tokens, too little for a long call.
- The Gemini free tier also includes Gemini 4 Hydrogen and Gemini 3.8 Flash (5 requests/min, 20/day): more accurate,
  fewer requests. We keep Hydrogen as a reference in section 2.
- **None of these free tiers is meant for production.** Cohere forbids it, Mistral presents it as an evaluation plan,
  and Google's 2025 terms allow only paid services for apps offered to users in the EEA. In production, option A means
  the paid tier of the same provider.

### 1.2 B — Powerful open models on a server

| Model | License | Size | Memory needed (4-bit) | Context |
|---|---|---|---|---|
| **Qwen3-Next-80B-A3B** (Alibaba) | Apache 2.0 | 80B total, 3B active per token | **64 GB of RAM** (48 GB download), or an 80 GB GPU | 262k |
| gpt-oss-120b (OpenAI) | Apache 2.0 | 117B total, 5.1B active | one **80 GB GPU** | 131k |
| Gemma 4 26B A4B (Google) | Apache 2.0 | 25.2B total, 3.8B active | **32 GB of RAM** (18 GB download), or a 24 GB GPU | 256k |

All three are mixture-of-experts models: only a few billion parameters work on each token, so they run at usable
speed even on a CPU server, as long as the whole model fits in memory. They are served with Ollama or vLLM, which expose
the same OpenAI-compatible API as the hosted providers, so our code does not change.

Not considered: the open models small enough for a laptop (they lose most of their accuracy on long calls, section 2.1),
and the largest open models (DeepSeek V4.1, Kimi K3, GLM-5.3), which need several data-center GPUs. Mistral Medium 3.5
(128B dense) and Command A+ (218B) are also open-weight, but too large for one affordable server.

### 1.3 B — Where to run the open model

| Option | Cost | Credit card | Fits | Good for |
|---|---|---|---|---|
| **Modal** (serverless GPU, pay per second) | ~$30 of free credits per month on the Starter plan (≈ 27 h of an A10 GPU or 12 h of an A100) | not required according to most sources; one source says a card is needed to get the full $30 | Qwen3-Next on an A100, Gemma 4 26B A4B on an A10 | development and **demo**; production at low volume |
| **Azure for Students** | $100 of credit for 12 months, with a university email | no | any of the three, on a VM with enough RAM or a GPU | **demo**, for a limited period |
| **Oracle Cloud Always Free** | free forever: 4 ARM cores, 24 GB of RAM | **yes**, for identity verification only | Gemma 4 26B A4B, tight | not for us (card) |
| **The company's own server** | no extra cost if it has 64 GB of RAM | – | Qwen3-Next | **production** |
| **Hetzner dedicated server** (Germany/Finland) | ~€59/month for 64 GB of RAM (EX44) | yes | Qwen3-Next, CPU only | **production** if no in-house server |

On Modal and Azure the data still passes through a cloud provider, but on infrastructure we control and that is not
used to train models. Azure and Hetzner offer EU data centers. On Modal, the model is loaded again after a period of
inactivity, so the first request after a pause waits a few minutes.

## 2. Comparison

### 2.1 Accuracy on our task: Voice Standard Bench

[Voice Standard Bench](https://github.com/AssemblyAI/voice-standard-bench) (AssemblyAI) is the public benchmark closest to
our task. Each model reads **real call transcripts** (text) and fills a JSON with participants, **action items (owner,
task, due date)**, decisions and entities. LLM judges grade each answer against the transcript for *groundedness*
(nothing invented) and *completeness* (nothing important missed); invalid JSON gets the worst score. There are 10
transcripts per call length; scores are 0–100. Numbers are the combined judges' scores for the information-extraction
task (`results/canonical/0-1-3/summary.csv`, generated 2026-11-17).

| Model | Option | 30 s | 2 min | 15 min | **60 min** |
|---|---|---|---|---|---|
| Gemini 4 Hydrogen *(free, 20 requests/day; reference)* | A | 88 | 86 | 76 | **75** |
| **Gemini 3.5 Flash-Lite**, default thinking | A | 84 | 80 | 63 | **59** |
| **Gemini 3.5 Flash-Lite**, low thinking | A | 81 | 85 | 68 | **63** |
| Mistral Medium 3.5 | A | not in the benchmark | | | |
| Command A+ | A | not in the benchmark | | | |
| **Qwen3-Next-80B-A3B** | B | 75 | 68 | 67 | **68** |
| gpt-oss-120b | B | 44 | 63 | 62 | **60** |
| Gemma 4 31B *(Gemma 4 26B A4B is not in the benchmark)* | B | 88 | 83 | 24 | **33** |
| *Laptop-class open models, for comparison: gpt-oss-20b* | – | 27 | 48 | 37 | **34** |

What the numbers say:

- **On long calls, Qwen3-Next-80B-A3B (B) is at least as accurate as Gemini 3.5 Flash-Lite (A)**: equal at 15 minutes
  (67 against 63–68), better at 60 minutes (68 against 59–63). Only Gemini 4 Hydrogen, with its 20 requests a day,
  does clearly better (75–76).
- **gpt-oss-120b is close to Flash-Lite on long calls but weak on short ones**, and needs an 80 GB GPU.
- **Gemma 4 is strong on short calls and weak on long ones** (its 31B sibling drops to 24–33); the 26B A4B is only a
  fallback for machines with 32 GB.
- **Mistral and Cohere are not in the benchmark.** On Artificial Analysis's general index (v5, Nov 2026), which is
  dominated by coding and agentic tasks, Flash-Lite scores 12, Command A+ 6 and Mistral Medium 3.5 5. Their accuracy on
  our task must come from our own test (2.4).

Limits of the benchmark: English transcripts, 10 per length, LLM judges, JSON requested only in the prompt (we enforce a
schema), and hosted setups that may differ from ours. Our transcripts will also be noisier (WhisperX `small` has a word
error rate of ~0.25 in `docs/WhisperX-vs-whisper_cpp.md`). Treat the numbers as a ranking, not as the accuracy we will get.

### 2.2 Practical comparison

| | A: Gemini 3.5 Flash-Lite | A: Mistral Medium 3.5 | A: Command A+ | B: Qwen3-Next-80B-A3B on a server |
|---|---|---|---|---|
| Accuracy on long calls (2.1) | 59–68 | not measured | not measured | **67–68** |
| Cost, development and demo | €0 | €0 | €0 | €0 on Modal or Azure for Students |
| Cost, production | paid tier: ~$0.01–0.02 per 90-min call | paid plan: ~$0.05 per call ($1.50 / $7.50 per 1M tokens) | production key with billing | company server: €0; Hetzner: ~€59/month |
| Credit card | no | no | no | no (Modal, Azure for Students); yes for Hetzner |
| Free-tier limits | 500 requests/day | 1 request/second | 1,000 calls/month | none |
| Data | goes to Google | goes to Mistral (training unless switched off) | goes to Cohere | **stays on our infrastructure** |
| Production allowed | only on the paid tier | only on a paid plan | no (trial key) | **yes** (Apache 2.0) |
| Speed | seconds | seconds | seconds | GPU: seconds; CPU-only: minutes; Modal after a pause: + a few minutes |
| Our effort | none | none | none | **high**: install, configure, update and monitor the model server |
| Risk of changes | provider can change quotas, prices or retire the model | same | same | none: the model we test is the model we run |

### 2.3 Pros and cons in short

**A — Hosted without a card**

- ✅ Nothing to install; ready today; a whole call fits in one request; JSON enforced by the API.
- ✅ Gemini 3.5 Flash-Lite gives 500 free requests a day, enough for the whole team to develop.
- ❌ Transcripts leave the company, and on the free tiers the data terms are weaker (Mistral trains on data by default).
- ❌ Not usable in production for free: production means the paid tier, i.e. a card and a contract with the provider.
- ❌ Quotas, prices and models can change without notice (Gemini's free tier was cut from 250 to 20 requests a day in
  December 2025).

**B — Powerful open model on a server**

- ✅ The best open option (Qwen3-Next-80B-A3B) is at least as accurate as Gemini 3.5 Flash-Lite on long calls, and
  better on the longest ones.
- ✅ Transcripts never reach an AI provider; no quotas; no license limits on commercial use.
- ✅ €0 for the demo (Modal or Azure for Students), and no per-call cost in production.
- ❌ Someone must deploy, configure and maintain the model server (and avoid pitfalls such as Ollama silently
  truncating long transcripts).
- ❌ Needs a machine with 64 GB of RAM or a large GPU; on CPU each call takes minutes.
- ❌ The free cloud credits are limited in time or amount, so production needs the company's own server or a paid one.

### 2.4 Our own test

Before deciding, run the same 5 transcripts produced by **our** pipeline (Fireflies or WhisperX, in Italian) through
Gemini 3.5 Flash-Lite, Mistral Medium 3.5 and Qwen3-Next-80B-A3B with `smoke_test.py`, and compare the extracted items
with a list written by hand: missing tasks, invented tasks, wrong owner, wrong date. Command A+ can join the test, but
its 1,000 calls a month are shared by the whole account.

## 3. Conclusion: we use Gemini 3.5 Flash-Lite

**We adopt Gemini 3.5 Flash-Lite (`gemini-3.5-flash-lite`) through the Gemini API free tier.** It is the option that
best fits our constraints, for these reasons:

1. **It costs nothing and needs no credit card.** The key comes from Google AI Studio; when the free quota is used up
   the API answers HTTP 429 and stops, so nothing can ever be charged.
2. **There is nothing to install or maintain.** Option B needs a machine with 64 GB of RAM (or a large GPU), a model
   server to deploy, configure, secure and monitor, and attention to pitfalls such as silently truncated transcripts.
   With Flash-Lite the team spends its time on the product, not on infrastructure.
3. **The free quota is more than enough.** 500 requests a day per project, and we make one request per transcript. With
   one project per developer the whole team can develop and test freely, while the other free options allow far less
   (Cohere: 1,000 calls a month for the whole account; Gemini 4 Hydrogen and 3.8 Flash: 20 requests a day).
4. **A whole call fits in one request.** 1M tokens of context and 250k tokens per minute, against ~15k–25k tokens for a
   90-minute call: no need to split transcripts.
5. **The JSON is reliable.** The API constrains the answer to our JSON schema, and we validate it with Pydantic.
6. **Its accuracy on our task is measured and competitive.** It is the only free hosted model in the Voice Standard
   Bench: 80–85 on calls up to 2 minutes, 63–68 at 15 minutes, 59–63 at 60 minutes. On long calls this is close to the
   best open model we could run on a server (Qwen3-Next-80B-A3B: 67–68) and to gpt-oss-120b (60), without the server.
   Mistral Medium 3.5 and Command A+ have no measurement on this task, and score lower on the general index (5–6
   against 12).
7. **It is the fastest option**: ~315–345 output tokens per second, so a call is processed in seconds.
8. **It has the best data terms among the free options.** For users in the EEA, Google applies its paid-service data
   terms to the free quota too, while Mistral's free plan trains on the data by default and Cohere's key is a trial.
9. **Going to production is cheap and needs no code change.** On the paid tier a 90-minute call costs about
   $0.01–0.02, i.e. a few euros a month at the expected volume.
10. **It keeps every door open.** Our code uses the OpenAI-compatible API, so moving to another model is a change of
    `LLM_MODEL` / `LLM_BASE_URL` in `.env`: Gemini 4 Hydrogen on the same key if long calls need more accuracy (75–76),
    or Qwen3-Next-80B-A3B on a server if transcripts must stay in-house.
11. **It is already verified.** A test request with `LLM_API_KEY` from the environment succeeded locally, with no
    credit card on the account.

**What to keep in mind**

- Accuracy drops on the longest calls (59–63 at 60 minutes). We check it on our own Italian transcripts (section 2.4);
  if it is not enough, we switch to Gemini 4 Hydrogen.
- The free tier is for development and the demo. Before real users rely on the system, billing must be enabled
  (Google's terms allow only paid services for apps offered to users in the EEA).
- The transcripts are sent to Google. If the company that will use the system does not allow that, the fallback is
  option B: Qwen3-Next-80B-A3B on its own server, with the same code.

---

## Appendix A — Setup

**Option A.** Get a key (none needs a card) and put it in `llm-service/.env`:

- Gemini: [aistudio.google.com/apikey](https://aistudio.google.com/apikey) → **Create API key** in a **project of your
  own**. Do not click "Set up billing"; check on the AI Studio **Projects** page that the project is on the Free tier.
- Mistral: [console.mistral.ai](https://console.mistral.ai), then switch off data training in the admin panel.
- Cohere: [dashboard.cohere.com](https://dashboard.cohere.com) (trial key, created automatically).

**Option B.** On the server, install Ollama, then:

```sh
ollama pull qwen3-next:80b
OLLAMA_CONTEXT_LENGTH=32768 OLLAMA_HOST=0.0.0.0 ollama serve
```

Ollama's default context is 4,096 tokens and longer prompts are **silently truncated**, so the context length must be
set. Expose port 11434 only to the extraction service (firewall or VPN), never to the internet. On a GPU server or on
Modal, vLLM is the usual alternative; it also serves an OpenAI-compatible API.

| Profile | `LLM_BASE_URL` | `LLM_MODEL` | `LLM_API_KEY` |
|---|---|---|---|
| Gemini | `https://generativelanguage.googleapis.com/v1beta/openai/` | `gemini-3.5-flash-lite` | AI Studio key |
| Mistral | `https://api.mistral.ai/v1` | `mistral-medium-latest` | Mistral key |
| Cohere | `https://api.cohere.ai/compatibility/v1` | `command-a-plus-05-2026` | Cohere trial key |
| Open model, Ollama on a server | `http://<server>:11434/v1` | `qwen3-next:80b` | any value |

`.env` files are git-ignored. Never put a key in `application.properties`, in code, in a commit or in a screenshot.
The extraction service makes one request per transcript, retries an HTTP 429 at most 3 times with backoff, then uses
the fallback model; unit tests and CI never call a real API.

## Appendix B — Smoke test

`llm-service/smoke_test.py` sends a transcript (a built-in Italian sample call, or a file), asks for the action items
with the JSON schema, validates the answer and prints latency, token usage and any rate-limit headers. It works with
every profile above.

```sh
uv run llm-service/smoke_test.py                 # one request with the built-in sample call
uv run llm-service/smoke_test.py --runs 5        # latency and JSON reliability over 5 requests
uv run llm-service/smoke_test.py --transcript src/main/resources/abc123.json   # Fireflies-like JSON or plain text
uv run llm-service/smoke_test.py --timeout 1800 --transcript long-call.txt    # CPU-only server: allow more time
```

Exit code 0 means every request returned valid JSON, 1 means at least one failed, 2 means `LLM_API_KEY` is missing.
If a long transcript shows about 4,096 input tokens with Ollama, the context was truncated. The last line of the output
is a table row: paste it below.

| Date | Model | Valid JSON | Avg latency | Max latency | Tokens in / out (avg) |
|---|---|---|---|---|---|

## Sources

- Task benchmark: [Voice Standard Bench repository and results](https://github.com/AssemblyAI/voice-standard-bench) ·
  [AssemblyAI, best LLM for extracting structured data from transcripts](https://www.assemblyai.com/blog/best-llm-for-extracting-structured-data-from-transcripts)
- Gemini: [3.5 Flash-Lite model card](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite) ·
  [free-tier numbers per model, Nov 2026](https://www.scriptbyai.com/gemini-api-free-tier-limits/) ·
  [rate limits and tiers](https://ai.google.dev/gemini-api/docs/rate-limits) ·
  [pricing](https://ai.google.dev/gemini-api/docs/pricing) ·
  [OpenAI compatibility](https://ai.google.dev/gemini-api/docs/openai) ·
  [Additional Terms, current](https://ai.google.dev/gemini-api/terms) and
  [Sept 2025 version](https://ai.google.dev/gemini-api/terms-archive/terms_09_25_25) ·
  [December 2025 quota cut](https://www.technobezz.com/news/google-slashes-free-gemini-api-requests-to-20-per-day-2025-12-20-gl06)
- Mistral: [free tier limits](https://help.mistral.ai/en/articles/225174-what-are-the-limits-of-the-free-tier) ·
  [Free mode, no card, data training opt-out](https://www.developersdigest.tech/models/free-api/mistral) ·
  [structured outputs](https://docs.mistral.ai/capabilities/structured-output/custom_structured_output/) ·
  [Artificial Analysis, Mistral Medium 3.5](https://artificialanalysis.ai/models/mistral-medium-3-5)
- Cohere: [Command A+](https://docs.cohere.com/docs/command-a-plus) ·
  [rate limits](https://docs.cohere.com/docs/rate-limits) ·
  [pricing (trial keys not for production)](https://cohere.com/pricing) ·
  [Compatibility API](https://docs.cohere.com/docs/compatibility-api) ·
  [structured outputs](https://docs.cohere.com/docs/structured-outputs) ·
  [Artificial Analysis, Cohere models](https://artificialanalysis.ai/providers/cohere)
- General index: [Artificial Analysis, Flash-Lite vs Gemma 4 31B](https://artificialanalysis.ai/models/comparisons/gemini-3-5-flash-lite-vs-gemma-4-31b)
- Excluded providers: [Claude API billing](https://support.claude.com/en/articles/8114531) ·
  [xAI, no free tier](https://www.developersdigest.tech/models/free-api/xai) ·
  [GitHub Models limits](https://docs.github.com/en/github-models/use-github-models/prototyping-with-ai-models) ·
  [OpenAI prepaid billing](https://help.openai.com/en/articles/8264778)
- Open models: [Qwen3-Next-80B-A3B on Ollama](https://ollama.com/library/qwen3-next:80b) ·
  [Qwen3-Next GGUF sizes](https://huggingface.co/Qwen/Qwen3-Next-80B-A3B-Thinking-GGUF/tree/main) ·
  [gpt-oss](https://www.tomshardware.com/tech-industry/artificial-intelligence/openai-intros-two-lightweight-open-model-language-models-that-can-run-on-consumer-gpus-optimized-to-run-on-devices-with-just-16gb-of-memory) ·
  [Gemma 4 model card](https://ai.google.dev/gemma/docs/core/model_card_4) ·
  [Gemma 4 memory (Unsloth)](https://unsloth.ai/docs/models/gemma-4)
- Hosting: [Modal pricing](https://modal.com/pricing) ·
  [Modal Starter credits, no card](https://aicreditmart.com/ai-credits-providers/modal-free-tier-how-to-get-30-month-in-compute-credits-2026/) ·
  [Modal credits and payment method](https://www.mintlify.com/cheahjs/free-llm-api-resources/providers/trial/modal) ·
  [free GPU hours per $30](https://amitpandeytiktok.github.io/awesome-free-compute-2026/compute.html) ·
  [Azure for Students offer](https://azure.microsoft.com/en-us/offers/ms-azr-0170p/) ·
  [Oracle Cloud Always Free resources](https://docs.oracle.com/iaas/Content/FreeTier/resourceref.htm) ·
  [Oracle free tier sign-up](https://docs.oracle.com/iaas/Content/FreeTier/freetier.htm) ·
  [Hetzner prices](https://valebyte.com/en/providers/hetzner/) ·
  [Hetzner price increase, June 2026](https://effloow.com/articles/hetzner-cloud-ai-gpu-server-guide-2026)
- Ollama: [context length](https://docs.ollama.com/faq) ·
  [structured outputs](https://docs.ollama.com/capabilities/structured-outputs)
