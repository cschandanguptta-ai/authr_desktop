# 04_DATA_SCHEMA

## Room SQL Tables
- **BookEntity (`books`)**: `id`, `title`, `genre`, `synopsis`, `outline`, `personaId`, `format`, `targetWordCount`, `narrativeStructure`, `compiledManuscript`, etc.
- **ChapterEntity (`chapters`)**: `id`, `bookId`, `chapterNumber`, `title`, `content`, `status`.
- **LorebookEntity (`lorebook`)**: `id`, `bookId`, `category`, `entryName`, `description`.

## SharedPreferences Keys
- `gemini_api_key`: Stores Gemini API Key.
- `hf_key`: Hugging Face API Token.
- `mistral_api_key`: Mistral API Key.
- `provider`: Primary active provider.
- `hero_name`, `genres`, `duration`, `hook`, `extras`: Draft properties for quick load.
- `plot_arc`, `allow_hybrid_plot_arcs`, `author_persona`: Draft persona variables.
