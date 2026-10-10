package dev.placeholder.beforeyoubuy

/**
 * Application for Robolectric tests (src/test/resources/robolectric.properties): app-start work is
 * switched off so start-up coroutines can't race test fixtures (docs/architecture.md, Pitfalls).
 */
class TestApplication : BeforeYouBuyApplication() {
    override fun onAppStart() = Unit
}
