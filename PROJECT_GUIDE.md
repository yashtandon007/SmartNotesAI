# Smart Notes AI: Project Guide & Interview Q&A

A complete explanation of the project, plus interview questions and answers. For setup, see [README.md](README.md).

## Tech stack

| Area | Choice | Why |
|---|---|---|
| Language | Kotlin 2.3 (compiled at language level 2.2) | Hilt 2.57 metadata support |
| UI | Jetpack Compose + Material 3 | Declarative, state-driven UI |
| Architecture | MVVM + Clean Architecture (ui / domain / data) | Testable, swappable layers |
| DI | Hilt | Standard Android DI, easy fakes in tests |
| Storage | Room + Flow | Reactive local database |
| Network | Retrofit 3 + kotlinx.serialization | Type-safe REST, no reflection |
| On-device AI | ML Kit GenAI (Gemini Nano via AICore) | Private, offline, free |
| Cloud AI | Gemini API (`gemini-3.5-flash-lite`) | Works on every device |
| Async | Coroutines + Flow | Structured concurrency, streaming |
| Tests | JUnit + MockK + coroutines-test | 23 unit tests |
| CI | GitHub Actions | Runs tests on every push |

## How each feature works

### 1. Notes (CRUD)
`NotesListScreen` observes `NotesRepository.observeNotes()`, a Room `Flow`. Any insert or edit updates the list automatically. The editor saves through `NoteEditorViewModel`, which exposes a single `UiState` (`StateFlow`) and receives user events: unidirectional data flow.

### 2. Summarise (hybrid AI)
`SummarizeNoteUseCase` → `AiEngine.summarize()`. The Hilt-bound `AiEngine` is `HybridAiEngine`:
1. Ask `MlKitOnDeviceSummarizer` if Gemini Nano is available on this device (AICore feature status).
2. If yes, summarise on-device: private, offline, no cost.
3. If not available, or it fails, fall back to `CloudAiEngine`.
4. The result includes which engine ran, and the UI shows it ("On-device" / "Cloud").

### 3. Auto-tag (structured output)
`SuggestTagsUseCase` asks Gemini with `responseMimeType = application/json` and a `responseSchema` (array of strings). The model must return valid JSON, so we decode it directly with kotlinx.serialization: no regex or text parsing. Tags are then normalised (lowercase, trimmed, de-duplicated, max 5).

### 4. Ask your notes (RAG)
`AskNotesUseCase`:
1. **Retrieve:** `TfIdfRetriever` scores every note against the question on the device (term frequency × inverse document frequency, cosine similarity) and keeps the top 3 above a threshold.
2. **No match?** Return a fixed "I couldn't find that in your notes" without calling the model. This saves cost and prevents made-up answers.
3. **Augment:** `Prompts.ask()` puts only those notes into the prompt, each labelled with its title, and tells the model to answer only from them and cite `[title]`.
4. **Generate:** Gemini answers; the UI shows the source notes as chips.

### 5. Streaming answers (SSE)
- `GeminiApi.streamGenerateContent` calls `:streamGenerateContent?alt=sse` with `@Streaming`, so Retrofit hands over the raw body without buffering.
- `parseSseLine()` turns each `data: {json}` line into a text piece (skips blanks, throws on a blocked response).
- `CloudAiEngine.answerStream()` is a cold `flow {}` reading lines, with `body.use {}` so the connection always closes, and `flowOn(Dispatchers.IO)`.
- `AiEngine.answerStream()` has a default implementation that emits the whole answer once, so other engines work unchanged.
- `AskViewModel` adds an empty bubble with sources and `isStreaming = true`, appends each piece, keeps partial text if it fails halfway, and rethrows `CancellationException`.
- `AskScreen` shows a `▍` cursor while streaming.

## Request flow (Ask)

```
User types question
  → AskViewModel.send()
  → AskNotesUseCase.stream()
      → NotesRepository.getAll()          (Room)
      → TfIdfRetriever.topK()             (on device)
      → AiEngine.answerStream(prompt)     (HybridAiEngine → CloudAiEngine)
          → Retrofit SSE → parseSseLine() → Flow<String>
  → UiState updated per chunk → Compose recomposes the bubble
```

## Security

- API key only in `local.properties` (git-ignored) → `BuildConfig.GEMINI_API_KEY`.
- Never committed; CI does not need it (tests use fakes).
- In production: call Gemini through a backend or Firebase AI Logic with App Check so the key is not in the APK.

## Testing

`./gradlew testDebugUnitTest` runs 23 tests:
- `TfIdfRetriever`: ranking, threshold, empty input.
- Use cases with a fake `AiEngine`: tag normalisation, no-match RAG path, prompt contents.
- `HybridAiEngine`: on-device used when available, cloud fallback when not or on error.
- `StreamingTest` (7): SSE parsing, chunk joining, mid-stream failure, no-match path.

---

# Interview Q&A

### Project overview
**Q: Tell me about this project in 30 seconds.**
A: Smart Notes AI is an Android notes app with hybrid generative AI. It summarises notes on-device with Gemini Nano when the phone supports it and falls back to the Gemini cloud API otherwise. It auto-tags notes using structured JSON output, and lets you ask questions about your notes using RAG, with answers streamed token by token. It's built with Compose, MVVM, Clean Architecture, Hilt, Room and Coroutines, with unit tests on CI.

**Q: Why did you build it?**
A: To get hands-on with AI on mobile: on-device models, cloud fallback, RAG, structured output and streaming, using a production-style Android architecture.

### Architecture
**Q: Explain the layers.**
A: `ui` (Compose screens + ViewModels), `domain` (pure Kotlin: models, interfaces, use cases, retriever, prompts) and `data` (Room, Retrofit, AI engines). Domain depends on nothing; data implements domain interfaces. That's dependency inversion, and it means use cases can be tested with fakes.

**Q: Why an `AiEngine` interface?**
A: The ViewModels and use cases don't care where AI runs. I can swap cloud, on-device, hybrid or a fake for tests through Hilt without changing callers.

**Q: How does state flow in the UI?**
A: Each ViewModel exposes one immutable `UiState` as a `StateFlow`. Screens collect it with `collectAsStateWithLifecycle` and send events back. One source of truth, easy to test.

**Q: Why use cases if they are small?**
A: They hold business rules (tag normalisation, the no-match RAG shortcut) out of the ViewModels, and they are reusable and testable without Android.

### On-device vs cloud AI
**Q: Why hybrid?**
A: On-device is private, offline, free and low-latency, but only some devices support Gemini Nano and the model is smaller. Cloud works everywhere and is more capable. Hybrid gives the best experience on each device.

**Q: How do you decide which one to use?**
A: Check the ML Kit feature status. If available (or downloadable), use Nano; otherwise, or on any error, use cloud. The result records which engine ran so the UI can show it.

**Q: What are the limits of Gemini Nano?**
A: Limited device support, smaller context window, fixed feature APIs (summarise, proofread, rewrite, image description), and the model may need a download first.

### RAG
**Q: What is RAG and why use it here?**
A: Retrieval-augmented generation: find relevant documents first, then give only those to the model. It keeps the prompt small (cheaper, faster), sends less personal data to the cloud, and reduces hallucination because the model must answer from given notes.

**Q: Why TF-IDF and not embeddings?**
A: TF-IDF is simple, fast, fully on-device and needs no model. For a personal notes app with few documents it's good enough. The retriever is behind an interface, so embeddings (semantic search) can replace it later; that's on the roadmap.

**Q: How do you stop hallucinations?**
A: Only retrieved notes go in the prompt; the prompt says to answer only from them and cite titles; if nothing matches, we don't call the model at all.

### Structured output
**Q: How do you get reliable tags?**
A: Gemini structured output: JSON mime type plus a response schema. The model returns valid JSON that maps straight to a Kotlin type. Then I still validate: trim, lowercase, de-duplicate, cap the count.

### Streaming
**Q: How does streaming work?**
A: The endpoint `streamGenerateContent?alt=sse` sends server-sent events. Retrofit's `@Streaming` gives the raw body; I read it line by line in a cold Flow, parse each `data:` line and emit the text. The ViewModel appends each chunk to the same message.

**Q: Why a cold Flow?**
A: Work starts only when collected and is cancelled when the collector goes away (e.g. the screen closes). `body.use {}` guarantees the connection closes.

**Q: What happens if the stream fails halfway?**
A: The ViewModel keeps the text received so far and marks the message with an error, instead of throwing it away. `CancellationException` is always rethrown so cancellation still works.

**Q: Why stream at all?**
A: Total time is similar, but the first words appear in under a second, so it feels much faster.

### Coroutines & threading
**Q: Which dispatchers?**
A: Room and Retrofit suspend functions are main-safe. The streaming read is blocking I/O, so it uses `flowOn(Dispatchers.IO)`. ViewModels launch in `viewModelScope`.

**Q: Why rethrow `CancellationException`?**
A: Swallowing it breaks structured concurrency: the coroutine would keep running after its scope was cancelled.

### Data & DI
**Q: Why Room with Flow?**
A: The list updates automatically on any change; no manual refresh.

**Q: How is Hilt set up?**
A: `AppModule` provides the database, DAO, Retrofit and `GeminiApi`, and binds `NotesRepository` and `AiEngine` to their implementations as singletons.

### Security
**Q: Where is the API key?**
A: In `local.properties`, which is git-ignored, injected into `BuildConfig`. For production I'd move calls behind a backend or Firebase AI Logic with App Check so no key ships in the APK.

**Q: What privacy choices did you make?**
A: Summaries on-device when possible; for Ask, only the top few relevant notes leave the device, not all notes.

### Testing & CI
**Q: How did you test AI code that isn't deterministic?**
A: I test my code, not the model: a fake `AiEngine` returns fixed answers, so I test prompts, parsing, fallback logic, tag normalisation, RAG retrieval and streaming state. 23 tests run on GitHub Actions.

**Q: Any CI problems?**
A: Two: the workflow only ran on `main` while the repo used `master`, and `gradlew` wasn't executable on Linux (exit 126). Fixed with both branches and `chmod +x gradlew`.

### Challenges
**Q: What was hard?**
A: Version compatibility: Hilt 2.57 didn't read Kotlin 2.3 metadata, and newer Hilt required AGP 9. I compiled at Kotlin language level 2.2 to keep a stable toolchain. Also the cloud model timed out, so I moved to a faster flash-lite model, and streaming fixed the perceived latency.

**Q: What would you improve next?**
A: Embedding-based semantic search, background auto-tagging with WorkManager, Compose UI and screenshot tests, and moving the key behind a backend.

**Q: How would this scale to thousands of notes?**
A: Pre-compute and store the TF-IDF index (or embeddings) in Room instead of recomputing per query, update it incrementally on save, and use a vector index for embeddings.
