# ADR-0003: Coexistence of Dual Data Entities (Story vs Book/Chapter)

## Status
Accepted

## Context
The application evolved from an initial 5-part short story writer ("Interactive Wizard") into an autonomous multi-chapter novel generator ("Autonomous Studio"). Migrating legacy 5-part stories directly into normalized chapters risked breaking user projects, autosave logic, and existing EPUB exports.

## Decision
Maintain two distinct data models in Room DB (`AppDatabase.kt`):
1. `Story`: A flat entity storing parts 1 through 5 (`part1..part5`), used exclusively by Interactive Wizard tab and legacy story autosaves.
2. `Book` + `Chapter`: A normalized relational entity model with one-to-many relationship (`Book.id == Chapter.bookId` with `CASCADE` delete), supporting arbitrary chapter lengths, serialization schedules, lorebooks, and multi-agent memory states.

## Alternatives (Evidenced in Code)
- *Hard Schema Migration*: Converting all legacy `Story` rows into `Book` + `Chapter` entries. Discarded to maintain backward compatibility with `AutosaveManager.kt` and existing Interactive Wizard UI layouts.

## Consequences
- **Positive**: Zero data corruption for existing users; Interactive Wizard remains fast and self-contained.
- **Negative**: Redundant code in `StoryDashboardScreen.kt`, dual export pathways, and potential developer confusion if entity scopes are misunderstood.

## Do Not Change Unless
- Interactive Wizard is completely rewritten to construct 5-chapter `Book` projects and legacy `StoryDao` is formally migrated and deprecated.

## Evidence
- `app/src/main/java/com/example/data/model/Story.kt:7-36` [FACT]
- `app/src/main/java/com/example/data/model/Book.kt:6-39` [FACT]
- `app/src/main/java/com/example/ui/screens/StoryDashboardScreen.kt:48` [FACT]
