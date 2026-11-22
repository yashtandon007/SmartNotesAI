# How Smart Notes AI works: your walkthrough

Read this with the code open. Each section says **which file to look at**, **what it does**, and **what an interviewer might ask**.

---

## 1. The big picture (2-minute pitch)

> "Smart Notes AI is an Android notes app where AI runs in a **hybrid** way. Summaries run **on-device with Gemini Nano** through ML Kit's GenAI API, so they're private, free and work offline. If the device doesn't support it, the app **falls back to the cloud Gemini API** automatically. Tags use Gemini's **structured JSON output**. And there's an **'Ask your notes'** chat that uses **RAG**: I retrieve the relevant notes locally with TF-IDF and send only those to the model, so answers are grounded in the user's own data and cite their sources. It's built with Clean Architecture, MVVM, Compose, Hilt, Room and coroutines, and has unit tests and CI."

Practise saying this out loud until it feels natural. It's your answer to "tell me about a project you built".

---

## 2. Layers: why the folders look like this

| Layer | Folder | Knows about Android? | Job |
|---|---|---|---|
| UI | `ui/` | Yes | Draw state, forward user events |
| Domain | `domain/` | **No** (pure Kotlin) | Business rules: use cases, retriever, prompts, interfaces |
| Data | `data/` | Yes | Talk to Room, Gemini API, ML Kit |

**Dependency rule:** UI → Domain ← Data. The domain defines *interfaces* (`AiEngine`, `NotesRepository`), and the data layer *implements* them. This is **dependency inversion**.

Why it matters:
- You can swap cloud ↔ on-device ↔ fake AI without touching the UI.
- The domain is pure Kotlin, so it's fast to unit-test (no emulator).

**Interview Q:** *Why put `AiEngine` in the domain and not the data layer?* Because use cases depend on it. If it lived in data, the domain would depend on data and you'd lose testability and swappability.

---

## 3. Follow one tap: "✨ Summarise"

1. **`NoteEditorScreen`**: the button calls `viewModel.summarize()`.
2. **`NoteEditorViewModel.runAi()`** sets `runningTask = SUMMARY`, so the UI shows a spinner, then calls the use case. It catches errors and turns them into a snackbar message. It **re-throws `CancellationException`**, because swallowing it breaks structured concurrency.
3. **`SummarizeNoteUseCase`** validates the input and calls `AiEngine.summarize()`.
4. **`HybridAiEngine`** decides where to run it:
   - Text ≥ 400 chars **and** `onDevice.status() == READY`: Gemini Nano runs on the phone.
   - Otherwise, or if the on-device call throws: **`CloudAiEngine`** handles it.
5. **`MlKitOnDeviceSummarizer`**: ML Kit returns Guava `ListenableFuture`s. `kotlinx-coroutines-guava`'s `.await()` turns them into suspend calls. If the model is only *downloadable*, it starts the download in the background and uses the cloud this time.
6. **`CloudAiEngine`** builds a `generateContent` request with a **system instruction**, and **`GeminiApi`** (Retrofit) sends it. An **OkHttp interceptor** adds the `x-goog-api-key` header.
7. The result comes back as `AiOutput(text, source)`. The VM updates the state, **saves to Room**, and the card shows "On-device · Gemini Nano" or "Cloud · Gemini".

**Interview Q:** *Why on-device first?* Privacy (notes are personal), cost (no API bill), latency and offline support. *Trade-offs?* Limited device support, smaller model, English/Japanese/Korean only, input-size limits. That's why there's a cloud fallback.

---

## 4. RAG: "Ask your notes" (`AskNotesUseCase`, `TfIdfRetriever`)

**Problem:** You can't send *all* notes to the LLM: it costs too much, it's slow, and context windows have limits.

**RAG = Retrieve → Augment → Generate**
1. **Retrieve:** `TfIdfRetriever.topK()` scores every note against the question and keeps the top 3.
   - **TF**: how often a word appears in a note.
   - **IDF**: rare words count more than common ones. `ln((N+1)/(df+1)) + 1`
   - **Cosine similarity**: compares the question vector with each note vector.
   - Title words are counted twice (a small "boost" trick).
2. **Augment:** `Prompts.answerPrompt()` puts only those notes into the prompt, capped at 2,000 characters per note.
3. **Generate:** Gemini answers with `ANSWER_SYSTEM`: *use only these notes, say so if the answer isn't there, cite titles.*

If nothing is relevant, the app **doesn't call the LLM at all**. That's cheaper, and it stops the model from inventing an answer.

**Interview Q:** *How would you improve retrieval?* Use **embeddings** (vector representations of meaning), so "flight" can match "plane ticket". Store the vectors (in Room or a vector DB) and use the same cosine similarity. The `topK()` API stays the same, so the use case doesn't change.

**Interview Q:** *How do you reduce hallucination?* Grounding with retrieved context, an explicit "say you don't know" instruction, source citations, and skipping generation when retrieval finds nothing.

---

## 5. Structured output: tags (`CloudAiEngine.suggestTags`)

Instead of asking for "comma-separated tags" and hoping, the request sets:
```kotlin
GenerationConfig(
    responseMimeType = "application/json",
    responseSchema = Schema(type = "ARRAY", items = Schema(type = "STRING")),
)
```
The model is **constrained** to return a JSON array of strings, which `kotlinx.serialization` decodes. There's still a fallback parser (defensive coding), and `SuggestTagsUseCase` cleans the results: lowercases them, strips `#`, de-duplicates, and keeps at most 5.

**Note:** Gemini 3.x models **ignore `temperature` / `top_p` / `top_k`**, so output is shaped with system instructions and schemas instead.

---

## 6. State and coroutines patterns to explain confidently

- **Single UI state** (`EditorUiState`, `AskUiState`): one immutable data class per screen, exposed as `StateFlow`, updated with `_state.update { it.copy(...) }`. This is the MVI-style unidirectional data flow you already know.
- **`collectAsStateWithLifecycle()`** stops collecting when the app is in the background.
- **Search** (`NotesListViewModel`): `debounce(200)` waits until typing pauses, `flatMapLatest` cancels the previous DB query, and `stateIn(WhileSubscribed(5000))` survives rotation and stops when the screen is gone.
- **Save on back**: `saveAndClose()` saves first, *then* signals the UI to navigate. If you navigated first, the ViewModel would be cleared and `viewModelScope` would cancel the save. A good bug story for interviews!
- **One-off events** (snackbar): the message lives in the state, and the screen shows it and then calls `onMessageShown()`.

---

## 7. Other building blocks

- **Hilt** (`di/AppModule.kt`): `@Provides` for things you construct (Room, Retrofit, OkHttp), `@Binds` for interface → implementation, and a `@GeminiModel` qualifier for a primitive `String`.
- **Room** (`data/local`): `@Upsert`, `Flow` queries that re-emit on change, and tags stored as a comma-joined string. *Improvement:* a separate tags table with a many-to-many relation.
- **Type-safe navigation** (`ui/navigation`): routes are `@Serializable` classes. `EditorRoute(noteId)` arguments land in `SavedStateHandle` under `"noteId"`.
- **Secrets**: the API key comes from `local.properties` into `BuildConfig` and is never committed. Be ready to say that *production* should proxy through a backend or Firebase AI Logic + App Check.

---

## 8. Tests (`app/src/test`)

| Test | What it proves |
|---|---|
| `TfIdfRetrieverTest` | Ranking, title matching, "no match" returns empty, tokenizer |
| `AskNotesUseCaseTest` | Only relevant notes reach the LLM; no LLM call when nothing matches |
| `HybridAiEngineTest` | On-device when ready, cloud fallback on unavailable or error, short notes skip on-device |
| `NoteEditorViewModelTest` | State updates, persistence, tag cleaning, error → message, save-and-close |

Techniques: a **fake repository** (simpler than mocking), **MockK** `coEvery`/`coVerify` for suspend functions, and a **`MainDispatcherRule`** that swaps `Dispatchers.Main` for a test dispatcher.

Run: `./gradlew testDebugUnitTest`. CI (`.github/workflows/android.yml`) runs tests, lint and builds the APK on every push.

---

## 9. Make it truly yours (do these yourself)

The roadmap in the README is your learning path. Each item is a real feature you can talk about:

1. **Streaming answers:** call `streamGenerateContent?alt=sse`, parse server-sent events with OkHttp, expose a `Flow<String>`, and append tokens in the chat UI.
2. **Embeddings for semantic search:** replace TF-IDF with an embedding model and keep `topK()`'s signature.
3. **Migrate to the Gemini Interactions API:** Google now recommends it for new projects, and `generateContent` is considered legacy.
4. **WorkManager:** auto-tag notes in the background when they're saved.
5. **Compose UI tests** for the editor screen.

When you've built at least one of these and pushed it to GitHub, the project is genuinely yours to talk about in depth. Then tell Claude to add it to your resume.

---

## 10. Setup checklist

1. Unzip the project and open it in Android Studio. If it suggests upgrading AGP or library versions, accept.
2. Copy `local.properties.example` to `local.properties` and add `GEMINI_API_KEY`.
3. Run the app and create 3–4 notes (a trip, work stand-up, interview prep).
4. Try Summarise, Suggest tags, then Ask AI: "When are my flights?"
5. Create a GitHub repo, `git init`, commit, push. Check that the Actions CI goes green.
6. Add screenshots or a short screen recording to the README (recruiters do look).
