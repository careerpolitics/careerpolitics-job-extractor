# Article Trending Refactor Notes

## Refactored package structure

- `api`
  - `TrendingController`: thin HTTP layer for trend discovery, headline discovery, and article generation.
  - `ApiExceptionHandler`: consistent Problem Details responses.
- `application`
  - `TrendingWorkflowService`: orchestrates the end-to-end trending pipeline.
  - `TrendSelectionService`: cooldown filtering and deduplication.
  - `TrendNormalizer`: shared normalization and slug creation.
  - `TrendingScheduler`: guarded scheduled execution with overlap protection.
- `domain`
  - `model`: immutable trend, headline, article, and publish result records.
  - `port`: interfaces for discovery, news lookup, generation, publishing, and history storage.
  - `request` / `response`: API contracts.
- `infrastructure/google`
  - `GoogleTrendsRssClient`: Google Trends RSS adapter.
  - `GoogleNewsRssClient`: Google News RSS adapter.
- `infrastructure/article`
  - `TemplateArticleGenerator`: default low-cost article generation strategy.
  - `OpenRouterArticleGenerator`: optional AI strategy.
  - `ArticleGeneratorFactory`: strategy selection bean.
- `infrastructure/publisher`
  - `CareerPoliticsArticlePublisher`: optional external publishing adapter.
- `infrastructure/persistence`
  - `JpaTrendHistoryStore`: cooldown history adapter.

## Design patterns now used

- **Service Layer**: `TrendingWorkflowService` coordinates the use case and keeps controllers thin.
- **Strategy**: `ArticleGenerator` supports template and OpenRouter implementations.
- **Factory**: `ArticleGeneratorFactory` selects the correct generator based on configuration.
- **Ports and Adapters**: external systems are isolated behind interfaces to reduce coupling.

## CPU bottlenecks identified and fixed

The original implementation used browser automation for trends and news scraping. That design was expensive because it combined:

- Selenium Chrome sessions inside Docker.
- Repeated retry loops around browser startup and scraping.
- Manual verification wait loops for anti-bot pages.
- Consent handling, scrolling, and DOM polling.

The refactor removes the browser entirely and replaces it with RSS and lightweight HTTP calls. This drops CPU usage sharply and also removes the need for a second Selenium container.

## Additional production-readiness improvements

- Lower default log verbosity and disabled Hibernate SQL spam.
- Graceful scheduler overlap protection with `AtomicBoolean`.
- Centralized exception handling with `ProblemDetail`.
- Configurable publishing, OpenRouter generation, scheduler cadence, and database pool sizing.
- Graceful shutdown enabled in `application.yaml`.

## Docker optimization suggestions

- Keep container CPU limited to `1.0` unless benchmarking proves more is needed.
- Keep memory in the `512M`-`768M` range for the application container.
- Use `G1GC`, `MaxRAMPercentage`, and `InitialRAMPercentage` as configured in `docker-compose.yaml` and `build.gradle`.
- Keep the scheduler disabled by default and prefer six-hour or slower intervals.
- If article generation with OpenRouter is enabled, prefer request timeouts and small trend batches.
