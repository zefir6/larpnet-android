package pl.larpnet.android.domain.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRouterTest {
    private fun route(url: String) = LinkRouter.classify(url, "larpnet.pl")

    @Test fun ownPostsAndProfiles() {
        assertEquals(LinkTarget.Resolve("https://larpnet.pl/display/abc-123"), route("https://larpnet.pl/display/abc-123"))
        assertEquals(LinkTarget.LocalProfile("zefir"), route("https://larpnet.pl/profile/zefir"))
        assertEquals(LinkTarget.LocalProfile("zefir"), route("https://larpnet.pl/@zefir"))
        assertEquals(LinkTarget.Hashtag("larp"), route("https://larpnet.pl/search?tag=larp"))
        assertEquals(LinkTarget.InstancePage("https://larpnet.pl/calendar"), route("https://larpnet.pl/calendar"))
    }

    @Test fun fediverseLinks() {
        for (url in listOf(
            "https://mastodon.social/@Gargron",
            "https://mastodon.social/@Gargron/113000000000000000",
            "https://mastodon.social/users/Gargron/statuses/113000000000000000",
            "https://friendica.example/display/0123abcd",
            "https://pleroma.example/notice/AbCd123",
            "https://misskey.example/notes/9xyz",
            "https://lemmy.example/post/42",
        )) {
            assertEquals(url, LinkTarget.Resolve(url), route(url))
        }
        assertEquals(LinkTarget.Hashtag("larp"), route("https://mastodon.social/tags/larp"))
    }

    @Test fun ordinaryLinksGoToTheBrowser() {
        assertTrue(route("https://example.com/blog/2026/11/hello") is LinkTarget.External)
        assertTrue(route("https://example.com/") is LinkTarget.External)
        assertTrue(route("mailto:a@b.pl") is LinkTarget.External)
    }

    @Test fun mastodonProtocol() {
        assertEquals(LinkTarget.Resolve("Gargron@mastodon.social"), route("web+mastodon://follow?uri=acct:Gargron@mastodon.social"))
        assertEquals(LinkTarget.Resolve("Gargron@mastodon.social"), route("web+mastodon://follow?uri=%40Gargron%40mastodon.social"))
        assertEquals(LinkTarget.Share("Hello world https://x.pl"), route("web+mastodon://share?text=Hello%20world&url=https%3A%2F%2Fx.pl"))
    }

    @Test fun webAp() {
        assertEquals(LinkTarget.Resolve("https://mastodon.social/users/Gargron"), route("web+ap://mastodon.social/users/Gargron"))
        assertEquals(LinkTarget.Resolve("https://example.com/ap/object/1"), route("web+ap://example.com/ap/object/1"))
    }

    @Test fun handles() {
        assertTrue(LinkRouter.isHandle("Gargron@mastodon.social"))
        assertTrue(!LinkRouter.isHandle("https://mastodon.social/@Gargron"))
    }

    @Test fun websiteBanner() {
        assertEquals(LinkTarget.Resolve("https://larpnet.pl/display/abc"), route("larpnet://open?url=https%3A%2F%2Flarpnet.pl%2Fdisplay%2Fabc"))
        assertEquals(LinkTarget.InstancePage("https://larpnet.pl/network"), route("larpnet://open?url=https%3A%2F%2Flarpnet.pl%2Fnetwork"))
        assertEquals(LinkTarget.InstancePage(""), route("larpnet://open"))
    }
}
