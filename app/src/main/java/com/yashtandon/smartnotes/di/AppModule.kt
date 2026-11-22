package com.yashtandon.smartnotes.di

import android.content.Context
import androidx.room.Room
import com.yashtandon.smartnotes.BuildConfig
import com.yashtandon.smartnotes.data.ai.HybridAiEngine
import com.yashtandon.smartnotes.data.ai.MlKitOnDeviceSummarizer
import com.yashtandon.smartnotes.data.local.NoteDao
import com.yashtandon.smartnotes.data.local.NotesDatabase
import com.yashtandon.smartnotes.data.remote.GeminiApi
import com.yashtandon.smartnotes.data.repository.NotesRepositoryImpl
import com.yashtandon.smartnotes.domain.ai.AiEngine
import com.yashtandon.smartnotes.domain.ai.OnDeviceSummarizer
import com.yashtandon.smartnotes.domain.repository.NotesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiModel

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NotesDatabase =
        Room.databaseBuilder(context, NotesDatabase::class.java, "smart_notes.db").build()

    @Provides
    fun provideNoteDao(db: NotesDatabase): NoteDao = db.noteDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true // the API adds fields over time; don't crash on them
        explicitNulls = false // omit null fields from requests
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            // Every Gemini request gets the key as a header (not a URL query param, which can leak into logs).
            val request = chain.request().newBuilder()
                .header("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS) // LLM responses can take a while
        .build()

    @Provides
    @Singleton
    fun provideGeminiApi(client: OkHttpClient, json: Json): GeminiApi = Retrofit.Builder()
        .baseUrl(GEMINI_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GeminiApi::class.java)

    @Provides
    @GeminiModel
    fun provideGeminiModel(): String = BuildConfig.GEMINI_MODEL
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindRepository(impl: NotesRepositoryImpl): NotesRepository

    @Binds
    abstract fun bindAiEngine(impl: HybridAiEngine): AiEngine

    @Binds
    abstract fun bindOnDeviceSummarizer(impl: MlKitOnDeviceSummarizer): OnDeviceSummarizer
}
