# 🎵 My YT Music (Android 14 / Clean Architecture / Jetpack Compose)

Современный нативный музыкальный плеер для Android с интеграцией **InnerTube API (YouTube Music)**, фоновым воспроизведением через **Media3 MediaLibraryService** и загрузчиком треков в память телефона (**MediaStore API / Scoped Storage** в папку `Music/MyYTMusic`).

---

## 🏛 Архитектура проекта (Clean Architecture + MVVM)

Приложение построено по строгим принципам **Clean Architecture** с разделением на слои:

```
yt-music-app/
├── domain/                    # Слой бизнес-логики (Entity, Models, Repository Interfaces)
│   ├── model/
│   │   ├── Track.kt           # Доменная модель трека и конвертер в MediaItem
│   │   ├── StreamInfo.kt      # Ссылка на аудиопоток, битрейт, mimeType
│   │   └── DownloadState.kt   # Состояния загрузки (Downloading, Completed, Error)
│   └── repository/
│       └── MusicRepository.kt # Интерфейс взаимодействия с источником музыки
├── data/                      # Слой данных (API, Сервисы, Хранилище)
│   ├── innertube/
│   │   ├── InnerTubeApi.kt    # Retrofit интерфейс к InnerTube API
│   │   ├── InnerTubeModels.kt # JSON DTO модели запросов и ответов
│   │   ├── InnerTubeClient.kt # OkHttp клиент с ретраями и Android Music заголовками
│   │   ├── CipherDecipherer.kt# Алгоритм расшифровки подписей и n-параметров
│   │   └── InnerTubeRepositoryImpl.kt # Реализация MusicRepository
│   ├── service/
│   │   ├── PlaybackService.kt # Фоновый сервис MediaLibraryService (Media3)
│   │   └── MediaSessionCallback.kt # Управление сессией, очередью и командами
│   ├── downloader/
│   │   ├── MusicDownloadManager.kt # Корутинный загрузчик с проверкой памяти (StatFs)
│   │   └── MediaStoreSaver.kt # Запись аудио в системную папку Music/MyYTMusic
│   └── local/
│       └── LocalMusicStorage.kt # Загрузка локальных треков из MediaStore
├── presentation/              # Слой пользовательского интерфейса (Jetpack Compose + MVVM)
│   ├── MainActivity.kt        # Главная активность с Compose Navigation
│   ├── viewmodel/
│   │   ├── MusicPlayerViewModel.kt # Управление воспроизведением и MediaController
│   │   ├── SearchViewModel.kt # Поиск и отображение треков
│   │   └── LibraryViewModel.kt # Управление локальными загрузками
│   ├── ui/
│   │   ├── theme/             # Material 3 Dark Theme
│   │   ├── screens/           # Экраны: SearchScreen, FullPlayerScreen, DownloadsScreen
│   │   └── components/        # Компоненты: MiniPlayerBar, TrackItemRow
│   └── util/
│       └── PermissionsHelper.kt # Разрешения Android 13/14 (POST_NOTIFICATIONS, READ_MEDIA_AUDIO)
└── .github/workflows/
    └── release.yml            # CI/CD автосборка и публикация релиза с подписью APK
```

---

## 🚀 Ключевые возможности

1. **Поиск и стриминг через InnerTube API:**
   * Неофициальный клиент YouTube Music с маскировкой под Android Music (`clientVersion: 6.42.52`).
   * Расшифровка потоковых ссылок и подписей (`CipherDecipherer.kt`).
   * Выбор наилучшего аудиопотока (AAC 128k / Opus 160k).

2. **Фоновое воспроизведение (Media3 MediaLibraryService):**
   * Работает при заблокированном экране и в свернутом состоянии.
   * Системное мультимедийное уведомление с кнопками управления.
   * Восстановление при пересоздании активности.

3. **Сохранение музыки в память телефона (`Music/MyYTMusic`):**
   * Загрузка через корутины и OkHttp чанками.
   * Предварительная проверка свободного места через `StatFs`.
   * Сохранение тегов (Название, Исполнитель, Альбом) в системный `MediaStore`.
   * Офлайн-воспроизведение без доступа к интернету.

4. **UI на Jetpack Compose:**
   * YouTube Music стиль в тёмных тонах Material 3.
   * Прикреплённый снизу MiniPlayer с индикатором прогресса.
   * Полноэкранный плеер с обложками альбомов и интерактивным слайдером перемотки.

---

## 🔑 Настройка подписи (Keystore) и Secrets в GitHub

Для автоматической сборки подписанного релизного APK настройте секреты в репозитории:

### 1. Генерация ключа (Keystore)
Если у вас ещё нет ключа, сгенерируйте его в терминале:
```bash
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
```

### 2. Кодирование ключа в Base64
```bash
base64 -w 0 my-release-key.jks > keystore.b64
```

### 3. Добавление секретов в GitHub
Перейдите в репозиторий: **Settings → Secrets and variables → Actions → New repository secret** и добавьте:

| Имя секрета | Значение |
|---|---|
| `KEYSTORE_BASE64` | Содержимое файла `keystore.b64` (весь текст в одну строку) |
| `KEYSTORE_PASSWORD` | Пароль от файла хранилища (keystore) |
| `KEY_ALIAS` | Имя алиаса ключа (например, `my-key-alias`) |
| `KEY_PASSWORD` | Пароль от самого ключа |

> *Примечание:* Если секреты не добавлены, GitHub Actions автоматически соберёт стандартный APK без сбоя сборки.

---

## 📦 Сборка и публикация релиза

### Автоматический релиз по тегу:
```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```
GitHub Actions автоматически скомпилирует приложение, создаст релиз на странице **Releases** и прикрепит готовый файл APK.

### Ручной запуск через интерфейс GitHub:
Перейдите во вкладку **Actions** → выберите workflow **«Build and Release APK»** → нажмите **«Run workflow»**.

---

## 🛠 Локальная сборка проекта

```bash
# Клонирование
git clone https://github.com/Klischa/yt-music-app.git
cd yt-music-app

# Сборка Debug APK
./gradlew assembleDebug

# Сборка Release APK
./gradlew assembleRelease
```
Файлы APK будут сгенерированы в папке `app/build/outputs/apk/`.
