# 🎵 My YT Music (Android 14 / Clean Architecture / Jetpack Compose)

Современный нативный музыкальный плеер для Android с интеграцией **YouTube Music (InnerTube API)**, фоновым воспроизведением через **Media3 MediaLibraryService + HTML5 Audio Engine**, поддержкой **плейлистов, лайков/дизлайков, синхронизированных караоке-текстов (LrcLib)** и загрузчиком треков в память телефона (`Music/MyYTMusic`).

---

## 🏛 Архитектура проекта (Clean Architecture + MVVM)

```
yt-music-app/
├── domain/                          # Слой бизнес-логики (Entity, Models, Repository Interfaces)
│   ├── model/
│   │   ├── Track.kt                 # Доменная модель трека и конвертер в MediaItem
│   │   ├── Playlist.kt              # Модель плейлиста
│   │   ├── LikeStatus.kt            # Состояние лайка (NONE, LIKED, DISLIKED)
│   │   ├── StreamInfo.kt            # Ссылка на аудиопоток, битрейт, mimeType
│   │   └── DownloadState.kt         # Состояния загрузки (Downloading, Completed, Error)
│   └── repository/
│       └── MusicRepository.kt       # Интерфейс репозитория музыки
├── data/                            # Слой данных (API, Сервисы, Хранилище)
│   ├── innertube/
│   │   ├── InnerTubeApi.kt          # Retrofit эндпоинты YouTube Music
│   │   ├── WatchNextRepository.kt   # Эндпоинт youtubei/v1/next (очередь и радио)
│   │   └── InnerTubeRepositoryImpl.kt # Реализация MusicRepository
│   ├── lyrics/
│   │   └── LyricsService.kt         # Синхронизированные караоке-тексты (LrcLib API)
│   ├── service/
│   │   ├── PlaybackService.kt       # MediaLibraryService (Media3) с WakeLock
│   │   └── YouTubeAudioWebView.kt   # Прямой HTML5 аудио-движок автозапуска
│   ├── local/
│   │   ├── PlaylistManager.kt       # Менеджер плейлистов, лайков и дизлайков
│   │   └── LocalMusicStorage.kt     # Считывание офлайн-треков из памяти
│   └── downloader/
│       ├── MusicDownloadManager.kt  # Загрузка треков с проверкой памяти (StatFs)
│       └── MediaStoreSaver.kt       # Запись тегов в MediaStore (Music/MyYTMusic)
├── presentation/                    # Слой UI (Jetpack Compose + MVVM)
│   ├── MainActivity.kt              # Навигация (Поиск | Медиатека | Загрузки)
│   ├── viewmodel/
│   │   ├── MusicPlayerViewModel.kt  # Управление плеером, очередью, лайками
│   │   ├── SearchViewModel.kt       # Поиск и тренды
│   │   └── LibraryViewModel.kt      # Офлайн-медиатека
│   └── ui/screens/                  # SearchScreen, LibraryScreen, FullPlayerScreen, DownloadsScreen
└── .github/workflows/
    └── release.yml                  # CI/CD автосборки и публикации релизов
```

---

## ✨ Ключевые возможности

1. **🎧 Плейлисты и Управление медиатекой:**
   * Вкладка **«Медиатека»**: создание, редактирование и удаление собственных плейлистов.
   * Добавление любого трека в плейлист в один клик.
   * Кнопка **«Слушать всё»** для воспроизведения всего плейлиста подряд.

2. **❤️ Лайки и 👎 Дизлайки:**
   * Нажатие на **👍 Лайк** мгновенно сохраняет трек в специальный плейлист **«Понравившиеся»**.
   * Нажатие на **👎 Дизлайк** помечает песню и пропускает её при автовоспроизведении.

3. **🎤 Синхронизированный караоке-текст песни (LrcLib API):**
   * Вкладка **«Текст»** в полноэкранном плеере с точностью до миллисекунд подсвечивает текущую строку и плавно скроллится в такт песне.
   * Нажатие на любую строчку текста моментально перематывает песню к этому моменту!

4. **📜 Бесконечная умная очередь (Watch Next):**
   * Автоматическая подгрузка похожих треков и рекомендаций для непрерывного фонового прослушивания.

5. **🔒 Непрерывное фоновое воспроизведение (WakeLock):**
   * Работа при выключенном экране, в играх, мессенджерах и при сворачивании приложения.

6. **💾 Загрузка музыки в телефон (`Music/MyYTMusic`):**
   * Офлайн-воспроизведение без доступа к сети.

---

## 🚀 Автоматическая сборка релизов

Сборка запускается автоматически при создании тега:
```bash
git tag -a v1.2.0 -m "Release v1.2.0 - Playlists, Liked Songs, Dislikes, and Synced Lyrics"
git push origin v1.2.0
```
Готовый APK будет прикреплен к разделу **Releases**.
