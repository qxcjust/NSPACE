package com.nspace.mediacenter.util;

import android.annotation.SuppressLint;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Lightweight client-side ad blocker for the in-app WebView.
 *
 * Design guarantees (why this never breaks page functionality):
 *  1. It is a WHITELIST of known third-party ad-network hosts. A resource is
 *     only blocked when its host exactly matches or ends with one of these
 *     domains. Anything else (media streams, login APIs, first-party assets,
 *     player scripts) passes through untouched — so playback / login / buttons
 *     can never be "functionally broken" by an over-broad rule.
 *  2. The WebViewClient only calls {@link #isAd(String)} for SUB-RESOURCES
 *     (images / scripts / css / iframes). The main document is never
 *     intercepted, so navigation itself is always preserved.
 *  3. Blocked requests return an EMPTY 200 response (not an error), so the
 *     surrounding page never throws or reloads because of a missing ad.
 *
 * Scope: removes banner / popup / floating / sidebar ADS (independent ad
 * resources). It does NOT and cannot remove SSAI server-inserted video
 * pre-roll (the ad is spliced into the same media stream as the content).
 */
public final class AdBlocker {

    private static final Set<String> AD_HOSTS = new HashSet<>();

    static {
        // Mainstream ad networks / ad-serving domains. Keep this list to
        // third-party AD providers only — never add first-party or media hosts.
        final String[] hosts = {
            "2mdn.net",
            "adcolony.com",
            "adform.net",
            "adgrx.com",
            "adnxs.com",
            "adscale.de",
            "adservice.google.com",
            "adservice.google.com.hk",
            "adsystem.com",
            "adsrvr.org",
            "adspirit.de",
            "advangelists.com",
            "advendor.net",
            "adview.com",
            "adyolly.com",
            "advertising.com",
            "amazon-adsystem.com",
            "appnexus.com",
            "behaviouralengine.com",
            "bidswitch.net",
            "bluekai.com",
            "casalemedia.com",
            "connect.facebook.net",
            "content.ad",
            "crispmedia.net",
            "criteo.com",
            "criteo.net",
            "districtm.net",
            "dotomi.com",
            "doubleclick.net",
            "exelator.com",
            "fluct.jp",
            "freewheel.tv",
            "genieessp.com",
            "gstaticad.com",
            "googleadservices.com",
            "googlesyndication.com",
            "googletagmanager.com",
            "googletagservices.com",
            "gravity.com",
            "gumgum.com",
            "indexexchange.com",
            "intentmedia.net",
            "invitemedia.com",
            "kargo.com",
            "ligatus.com",
            "linkstorm.net",
            "lijit.com",
            "mathtag.com",
            "media.net",
            "mgid.com",
            "moatads.com",
            "nativeads.com",
            "netseer.com",
            "openx.net",
            "plista.com",
            "pointroll.com",
            "popads.net",
            "propellerads.com",
            "pubmatic.com",
            "q1media.com",
            "quantserve.com",
            "revcontent.com",
            "rbmy.com",
            "resin.com",
            "rmxads.com",
            "rubiconproject.com",
            "scorecardresearch.com",
            "servebom.com",
            "sharethrough.com",
            "smartadserver.com",
            "sonobi.com",
            "specificmedia.com",
            "spellout.net",
            "spotx.tv",
            "spotxchange.com",
            "steelhouse.com",
            "synacor.com",
            "taboola.com",
            "tattrnet.com",
            "teads.tv",
            "turn.com",
            "underdogmedia.com",
            "unrulymedia.com",
            "valueclick.com",
            "vexperts.com",
            "vidible.tv",
            "w55c.net",
            "xaxis.com",
            "yieldlab.net",
            "yieldmo.com",
            "zergnet.com",
            "zedo.com",
        };
        for (final String h : hosts) {
            AD_HOSTS.add(h);
        }
    }

    private AdBlocker() {
        // utility class
    }

    /**
     * @return true if the URL belongs to a known third-party ad network and
     *         should be blocked. Returns false for anything unrecognized so
     *         that first-party / media / API traffic is never touched.
     */
    public static boolean isAd(final String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        final String lower = url.toLowerCase();
        // Fast bail-out: only http(s) resources can be ads.
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        int hostStart = lower.indexOf("://") + 3;
        if (hostStart < 3) {
            return false;
        }
        int hostEnd = lower.indexOf('/', hostStart);
        final String host = (hostEnd < 0) ? lower.substring(hostStart)
                : lower.substring(hostStart, hostEnd);
        for (final String ad : AD_HOSTS) {
            if (host.equals(ad) || host.endsWith("." + ad)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build an empty successful response for blocked resources. Returning a
     * 200 with no body makes the ad "load" as nothing instead of erroring out,
     * so the page never sees a failure.
     */
    @SuppressLint("NewApi")
    public static WebResourceResponse emptyResponse() {
        return new WebResourceResponse(
                "text/plain",
                StandardCharsets.UTF_8.name(),
                new ByteArrayInputStream(new byte[0]));
    }
}
