# Article Trending Refactor Notes

## Refactored package structure

- `api`
  - `TrendingController`: thin HTTP layer for trend discovery, headline discovery, and article generation.
  - `SwaggerRedirectController`: redirects browser traffic to Swagger UI.
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
- `infrastructure/selenium`
  - `SeleniumBrowserClient`: bounded remote-browser loader with reduced interaction logic.
  - `GoogleTrendsSeleniumClient`: Google Trends adapter.
  - `GoogleNewsSeleniumClient`: Google Search News adapter.
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
- **Ports and Adapters**: Selenium, publishing, and persistence are isolated behind interfaces.

## CPU bottlenecks identified and mitigated

Selenium remains the required scraping mechanism, so the refactor focuses on reducing the known hotspots instead of removing the browser:

- Browser startup retries are capped.
- Interaction logic is reduced to light scrolling instead of aggressive simulated browsing.
- Manual bot-verification waits are disabled by default.
- Scheduler overlap is blocked with an `AtomicBoolean`.
- Chrome and app containers are both CPU and memory constrained in Docker.

## Additional production-readiness improvements

- Lower default log verbosity and disabled Hibernate SQL spam.
- Centralized exception handling with `ProblemDetail`.
- Swagger/OpenAPI UI restored so the REST API can be explored and executed directly in the browser.
- Configurable Selenium, publishing, OpenRouter generation, scheduler cadence, and database pool sizing.
- Graceful shutdown enabled in `application.yaml`.

## Docker optimization suggestions

- Keep Selenium sessions at one concurrent browser in the standalone container.
- Prefer `SELENIUM_HEADLESS=true` in production.
- Keep `SELENIUM_MAX_ATTEMPTS` at `1` or `2`.
- Keep scheduler cadence at six hours or slower unless a tighter SLA is required.
