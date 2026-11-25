package de.lagebild.app.di

import android.content.Context
import androidx.room.Room
import de.lagebild.app.BuildConfig
import de.lagebild.app.data.local.AppDatabase
import de.lagebild.app.data.local.ArticleDao
import de.lagebild.app.data.local.NotifiedDao
import de.lagebild.app.data.local.TranslationDao
import de.lagebild.app.data.local.UserStateDao
import de.lagebild.app.data.local.WarningDao
import de.lagebild.app.data.remote.GitHubApi
import de.lagebild.app.data.remote.HttpFetcher
import de.lagebild.app.data.remote.NinaApi
import de.lagebild.app.data.remote.OkHttpFetcher
import de.lagebild.app.data.remote.VbbApi
import de.lagebild.app.data.repository.DefaultSourceProvider
import de.lagebild.app.data.repository.SourceProvider
import de.lagebild.app.data.settings.SettingsRepository
import de.lagebild.app.data.settings.SettingsStore
import de.lagebild.app.data.translation.DeepLTranslator
import de.lagebild.app.data.translation.MlKitTranslator
import de.lagebild.app.data.translation.TextTranslator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun okHttp(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
        .addInterceptor { chain ->
            // Eigene Kennung: transport.rest und andere offene APIs bitten darum.
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Lagebild/${BuildConfig.VERSION_NAME} (Android; private News-App)")
                    .build(),
            )
        }
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
        }
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun ninaApi(client: OkHttpClient, json: Json): NinaApi =
        retrofit("https://warnung.bund.de/api31/", client, json).create(NinaApi::class.java)

    @Provides
    @Singleton
    fun vbbApi(client: OkHttpClient, json: Json): VbbApi =
        retrofit("https://v6.vbb.transport.rest/", client, json).create(VbbApi::class.java)

    @Provides
    @Singleton
    fun gitHubApi(client: OkHttpClient, json: Json): GitHubApi =
        retrofit("https://api.github.com/", client, json).create(GitHubApi::class.java)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "lagebild.db")
            // Cache-Tabellen dürfen bei einem kaputten Downgrade neu angelegt werden.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun articleDao(db: AppDatabase): ArticleDao = db.articleDao()
    @Provides fun userStateDao(db: AppDatabase): UserStateDao = db.userStateDao()
    @Provides fun translationDao(db: AppDatabase): TranslationDao = db.translationDao()
    @Provides fun warningDao(db: AppDatabase): WarningDao = db.warningDao()
    @Provides fun notifiedDao(db: AppDatabase): NotifiedDao = db.notifiedDao()

    @Provides @Named("mlkit") fun mlKit(t: MlKitTranslator): TextTranslator = t
    @Provides @Named("deepl") fun deepL(t: DeepLTranslator): TextTranslator = t
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun fetcher(impl: OkHttpFetcher): HttpFetcher
    @Binds abstract fun sources(impl: DefaultSourceProvider): SourceProvider
    @Binds abstract fun settingsStore(impl: SettingsRepository): SettingsStore
}
