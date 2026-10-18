package io.github.locngu.beforeyoubuy.core.domain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share capture (#15). The fixtures are written to look like what each app sends (`EXTRA_TEXT`, `EXTRA_SUBJECT`),
 * not captured from a device; replace them with real samples when you have them.
 */
class SharedTextTest {
    private fun parse(text: String, subject: String? = null) = parseSharedText(text, subject)

    // --- Fixtures, per app ---

    @Test
    fun `Amazon app, German`() {
        val draft = parse(
            "Schau dir dieses Angebot auf Amazon an\n\n" +
                "Sony WH-1000XM5 Kabellose Bluetooth Noise Cancelling Kopfhörer, Schwarz\n\n" +
                "https://amzn.eu/d/7xKpQ2b"
        )
        assertEquals("Sony WH-1000XM5 Kabellose Bluetooth Noise Cancelling Kopfhörer, Schwarz", draft.name)
        assertEquals("https://amzn.eu/d/7xKpQ2b", draft.url)
    }

    @Test
    fun `Amazon app, German, with a full product link`() {
        val draft = parse(
            "Schau dir das an: Sony WH-1000XM5 Kabellose Kopfhörer " +
                "https://www.amazon.de/dp/B09Y2MYL5C/ref=cm_sw_r_as_gl_api_gl_i_7XKPQ2B?linkCode=ml1&tag=friend-21&th=1"
        )
        assertEquals("Sony WH-1000XM5 Kabellose Kopfhörer", draft.name)
        assertEquals("https://www.amazon.de/dp/B09Y2MYL5C?th=1", draft.url)
    }

    @Test
    fun `Amazon app, English`() {
        val draft = parse(
            "Check out this deal on Amazon\n\nApple AirPods Pro (2nd Generation) Wireless Earbuds\nhttps://a.co/d/3fGh7Jk"
        )
        assertEquals("Apple AirPods Pro (2nd Generation) Wireless Earbuds", draft.name)
        assertEquals("https://a.co/d/3fGh7Jk", draft.url)
    }

    @Test
    fun `Amazon app, English, with a colon`() {
        val draft = parse(
            "Check out this product on Amazon: Kindle Paperwhite (16 GB) https://www.amazon.co.uk/dp/B0CFPJYX7P"
        )
        assertEquals("Kindle Paperwhite (16 GB)", draft.name)
        assertEquals("https://www.amazon.co.uk/dp/B0CFPJYX7P", draft.url)
    }

    @Test
    fun `Amazon search result in a browser keeps the product path`() {
        val draft = parse(
            "https://www.amazon.de/Sony-WH-1000XM5-Kopfh%C3%B6rer/dp/B09Y2MYL5C/ref=sr_1_1" +
                "?crid=2XYZ&dib=eyJ2&dib_tag=se&keywords=sony+wh-1000xm5&qid=1729000000&sprefix=sony%2Caps%2C98&sr=8-1",
            "Sony WH-1000XM5 Kabellose Kopfhörer : Amazon.de: Elektronik & Foto"
        )
        assertEquals("Sony WH-1000XM5 Kabellose Kopfhörer", draft.name)
        assertEquals("https://www.amazon.de/Sony-WH-1000XM5-Kopfh%C3%B6rer/dp/B09Y2MYL5C", draft.url)
    }

    @Test
    fun `eBay app, German`() {
        val draft = parse(
            "Schau dir diesen Artikel bei eBay an: LEGO Technic 42115 Lamborghini Sián FKP 37 NEU OVP " +
                "https://www.ebay.de/itm/204583920114?mkcid=16&mkevt=1&mkrid=707-127634-2357-0" +
                "&ssspo=dAbC&sssrc=2047675&ssuid=xYz&widget_ver=artemis&media=COPY"
        )
        assertEquals("LEGO Technic 42115 Lamborghini Sián FKP 37 NEU OVP", draft.name)
        assertEquals("https://www.ebay.de/itm/204583920114", draft.url)
    }

    @Test
    fun `eBay app, English`() {
        val draft = parse("Check out this item on eBay! Nintendo Switch OLED Model White https://ebay.us/m/AbC123")
        assertEquals("Nintendo Switch OLED Model White", draft.name)
        assertEquals("https://ebay.us/m/AbC123", draft.url)
    }

    @Test
    fun `Zalando app`() {
        val draft = parse(
            "Schau mal, was ich bei Zalando gefunden habe: Nike Sportswear AIR FORCE 1 '07 - Sneaker low - white " +
                "https://www.zalando.de/nike-sportswear-air-force-1-07-sneaker-low-white-ni112o0bs-a11.html" +
                "?utm_source=app&utm_medium=share&utm_campaign=pdp"
        )
        assertEquals("Nike Sportswear AIR FORCE 1 '07 - Sneaker low - white", draft.name)
        assertEquals(
            "https://www.zalando.de/nike-sportswear-air-force-1-07-sneaker-low-white-ni112o0bs-a11.html",
            draft.url
        )
    }

    @Test
    fun `Zalando in a browser drops the shop from the title`() {
        val draft = parse(
            "https://www.zalando.de/nike-sportswear-air-force-1-07-sneaker-low-white-ni112o0bs-a11.html",
            "Nike Sportswear AIR FORCE 1 '07 - Sneaker low - white - Zalando.de"
        )
        assertEquals("Nike Sportswear AIR FORCE 1 '07 - Sneaker low - white", draft.name)
    }

    @Test
    fun `Chrome sends the link as text and the title as subject`() {
        val draft = parse(
            "https://www.otto.de/p/philips-airfryer-xxl-hd9650-90-S0A1B2C3/#variationId=S0A1B2C3D4E5",
            "Philips Airfryer XXL HD9650/90 | OTTO"
        )
        assertEquals("Philips Airfryer XXL HD9650/90", draft.name)
        assertEquals(
            "https://www.otto.de/p/philips-airfryer-xxl-hd9650-90-S0A1B2C3/#variationId=S0A1B2C3D4E5",
            draft.url
        )
    }

    @Test
    fun `Chrome link to highlighted text`() {
        val draft = parse(
            "\"Kabellose Kopfhörer mit Noise Cancelling\"\n" +
                "https://www.example.de/p/123?utm_source=chrome#:~:text=Kabellose%20Kopfh%C3%B6rer",
            "Kopfhörer – Example Shop"
        )
        assertEquals("Kabellose Kopfhörer mit Noise Cancelling", draft.name)
        assertEquals("https://www.example.de/p/123", draft.url)
    }

    @Test
    fun `Firefox sends the link as text and the title as subject`() {
        val draft = parse(
            "https://www.ebay.de/itm/204583920114?_trkparms=amclksrc%3DITM&_trksid=p4429486.c101224",
            "LEGO Technic 42115 Lamborghini Sián FKP 37 NEU OVP | eBay"
        )
        assertEquals("LEGO Technic 42115 Lamborghini Sián FKP 37 NEU OVP", draft.name)
        assertEquals("https://www.ebay.de/itm/204583920114", draft.url)
    }

    @Test
    fun `Samsung Internet sends title and link as text`() {
        val draft = parse(
            "KALLAX Regal, weiß, 77x147 cm - IKEA\nhttps://www.ikea.com/de/de/p/kallax-regal-weiss-80275887/",
            "KALLAX Regal, weiß, 77x147 cm - IKEA"
        )
        assertEquals("KALLAX Regal, weiß, 77x147 cm", draft.name)
        assertEquals("https://www.ikea.com/de/de/p/kallax-regal-weiss-80275887/", draft.url)
    }

    // --- Edge cases ---

    @Test
    fun `no link`() {
        val draft = parse("Fahrradhelm, blau, Größe M")
        assertEquals("Fahrradhelm, blau, Größe M", draft.name)
        assertNull(draft.url)
        assertNull(draft.shop)
    }

    @Test
    fun `only a link gives the shop for a fallback name`() {
        val draft = parse("https://www.amazon.de/dp/B09Y2MYL5C")
        assertNull(draft.name)
        assertEquals("https://www.amazon.de/dp/B09Y2MYL5C", draft.url)
        assertEquals("amazon.de", draft.shop)
        assertEquals("amzn.eu", parse("https://amzn.eu/d/7xKpQ2b").shop)
        assertEquals("zalando.de", parse("https://m.zalando.de/x.html").shop)
    }

    @Test
    fun `only a link with boilerplate gives no name`() {
        assertNull(parse("Check out this deal on Amazon https://a.co/d/3fGh7Jk").name)
        assertNull(parse("Schau dir das an! https://amzn.eu/d/7xKpQ2b").name)
    }

    @Test
    fun `the first of several links, and no link in the name`() {
        val draft = parse("Lampe https://shop.example/a?utm_source=x und https://other.example/b noch eine")
        assertEquals("https://shop.example/a", draft.url)
        assertEquals("Lampe und noch eine", draft.name)
    }

    @Test
    fun `emoji stay in the name`() {
        val draft = parse("😍 Nike Air Max 90 👟 https://www.nike.com/de/t/air-max-90-schuh-abc")
        assertEquals("😍 Nike Air Max 90 👟", draft.name)
    }

    @Test
    fun `a line of emoji only is no name`() {
        val draft = parse("👀🔥\nhttps://www.nike.com/de/t/air-max-90-schuh-abc", "Nike Air Max 90")
        assertEquals("Nike Air Max 90", draft.name)
    }

    @Test
    fun `a very long text is cut at a word`() {
        val long = "Sehr langer Produktname " + "mit vielen Wörtern ".repeat(40)
        val name = parse("$long https://shop.example/p/1").name!!
        assertTrue(name.length <= MAX_SHARED_NAME_LENGTH + 1)
        assertTrue(name.endsWith("…"))
        assertTrue(name.startsWith("Sehr langer Produktname mit vielen"))
        assertTrue(name.removeSuffix("…").endsWith("Wörtern") || name.removeSuffix("…").endsWith("vielen"))
    }

    @Test
    fun `a very long word is cut between characters, never inside an emoji`() {
        val name = parse("a".repeat(MAX_SHARED_NAME_LENGTH - 1) + "👨‍👩‍👧" + "b".repeat(50)).name!!
        assertEquals("a".repeat(MAX_SHARED_NAME_LENGTH - 1) + "…", name)
    }

    @Test
    fun `a very long share still finds its link`() {
        val draft = parse("x".repeat(5_000) + " https://shop.example/p/1 " + "y".repeat(50_000))
        assertEquals("https://shop.example/p/1", draft.url)
    }

    @Test
    fun `garbage gives an empty draft`() {
        for (text in listOf("", "   ", "\n\n", "🙂", "!!! ???", "\u0000\u0001\u200B", "https://", "http:// ?!")) {
            assertEquals("for \"$text\"", SharedItemDraft(), parse(text))
        }
    }

    @Test
    fun `the subject is the fallback for the name and the link`() {
        assertEquals("Lampe", parse("", "Lampe").name)
        assertEquals("Lampe", parse("   ", "Lampe").name)
        val draft = parse("", "Lampe https://shop.example/p/1")
        assertEquals("Lampe", draft.name)
        assertEquals("https://shop.example/p/1", draft.url)
    }

    @Test
    fun `the text's name wins over the subject`() {
        assertEquals("Lampe", parse("Lampe https://shop.example/p/1", "Shop Example – Startseite").name)
    }

    @Test
    fun `punctuation after a link isn't part of it`() {
        assertEquals("https://shop.example/p/1", parse("Look: https://shop.example/p/1.").url)
        assertEquals("https://shop.example/p/1", parse("(https://shop.example/p/1)").url)
        assertEquals("https://de.wikipedia.org/wiki/Foo_(Bar)", parse("https://de.wikipedia.org/wiki/Foo_(Bar)!").url)
        assertEquals("https://shop.example/p/1", parse("„https://shop.example/p/1“").url)
    }

    @Test
    fun `the scheme is lower-cased, http stays http`() {
        assertEquals("https://Shop.Example/P/1", parse("HTTPS://Shop.Example/P/1").url)
        assertEquals("http://shop.example/p/1", parse("http://shop.example/p/1").url)
    }

    @Test
    fun `query parameters that aren't tracking stay, in order`() {
        val draft = parse("https://shop.example/p?color=blue&utm_source=x&size=42&fbclid=abc&gclid=def&Ref=home")
        assertEquals("https://shop.example/p?color=blue&size=42", draft.url)
    }

    @Test
    fun `Amazon's search parameters only go on Amazon`() {
        assertEquals(
            "https://shop.example/search?keywords=lamp",
            parse("https://shop.example/search?keywords=lamp").url
        )
    }

    @Test
    fun `boilerplate never eats the first words of a name`() {
        assertEquals("Check out these Sony headphones", parse("Check out these Sony headphones https://x.example").name)
        assertEquals("Schau dir diese Lampe", parse("Schau dir diese Lampe").name)
    }

    @Test
    fun `a name that starts with the shop's name stays`() {
        val draft = parse("https://www.amazon.de/dp/B0B1", "Amazon Basics USB-C Kabel | Amazon.de")
        assertEquals("Amazon Basics USB-C Kabel", draft.name)
        assertEquals("Sony Kopfhörer", parse("https://www.amazon.de/dp/B0B1", "Amazon.de: Sony Kopfhörer").name)
    }

    @Test
    fun `whitespace is collapsed and quotes trimmed`() {
        assertEquals("Blaue Lampe groß", parse("  „Blaue   Lampe\tgroß“  ").name)
    }

    @Test
    fun `quotes inside a name stay`() {
        assertEquals("Tasse „Kaffee“", parse("https://www.otto.de/p/1", "Tasse „Kaffee“ | OTTO").name)
        assertEquals("Monitor 27\"", parse("Monitor 27\" - Shop\nhttps://www.shop.de/p/1").name)
        assertEquals("Poster \"Sunset\"", parse("Poster \"Sunset\"").name)
        assertEquals("\"Sunset\" Poster \"XL\"", parse("\"Sunset\" Poster \"XL\"").name)
    }

    @Test
    fun `Amazon's US title puts the shop in front and the category after`() {
        val draft =
            parse(
                "https://www.amazon.com/dp/B09XS7JWHH",
                "Amazon.com: Sony WH-1000XM5 Wireless Headphones : Electronics"
            )
        assertEquals("Sony WH-1000XM5 Wireless Headphones", draft.name)
    }

    @Test
    fun `a shop name glued to the product stays`() {
        assertEquals(
            "eBay:LEGO Technic 42115 - Neu",
            parse("https://www.ebay.de/itm/1", "eBay:LEGO Technic 42115 - Neu").name
        )
    }

    @Test
    fun `a one-letter short link host isn't taken for the shop's name`() {
        val draft = parse("Check out this deal on Amazon\n\nA - Z Alphabet Puzzle\nhttps://a.co/d/xyz")
        assertEquals("A - Z Alphabet Puzzle", draft.name)
        assertEquals("a.co", draft.shop)
    }

    @Test
    fun `a highlighted-text link after a page's own anchor keeps the anchor`() {
        val draft = parse("https://shop.example/p/1#details:~:text=Kabellose%20Kopfh%C3%B6rer")
        assertEquals("https://shop.example/p/1#details", draft.url)
    }

    @Test
    fun `many closing brackets after a link are trimmed quickly`() {
        val draft = parse("https://x.de/a" + ")".repeat(9_000))
        assertEquals("https://x.de/a", draft.url)
    }
}
