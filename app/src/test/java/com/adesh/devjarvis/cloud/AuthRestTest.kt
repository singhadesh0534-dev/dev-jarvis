package com.adesh.devjarvis.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRestTest {
    @Test fun pkceChallengeIsTheRfcExample() {
        // RFC 7636 appendix B
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", AuthRest.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        assertTrue(AuthRest.newVerifier().length >= 43)
    }

    @Test fun googleUrlAndCallback() {
        val u = AuthRest.googleUrl("https://x.supabase.co/", "v")
        assertTrue(u.startsWith("https://x.supabase.co/auth/v1/authorize?provider=google&redirect_to=devjarvis%3A%2F%2Flogin-callback"))
        assertTrue(u.endsWith("code_challenge_method=s256"))
        assertEquals("abc", AuthRest.callbackCode("devjarvis://login-callback?code=abc").getOrNull())
        assertEquals("Access denied", AuthRest.callbackCode("devjarvis://login-callback#error=x&error_description=Access+denied").exceptionOrNull()?.message)
        assertTrue(AuthRest.callbackCode("https://evil/?code=1").isFailure)
    }

    @Test fun sessionParsing() {
        val s = AuthRest.session("""{"access_token":"A","refresh_token":"R","expires_in":3600,"user":{"id":"U","email":"a@b.co"}}""", now = 0)
        assertNotNull(s)
        assertEquals("U", s!!.userId); assertEquals("a@b.co", s.email); assertEquals(3_600_000L, s.expiresAt)
        assertNull(AuthRest.session("""{"id":"U","email":"a@b.co"}"""))
    }

    @Test fun googleSwitch() {
        assertEquals(false, AuthRest.googleEnabled("""{"external":{"email":true,"google":false},"disable_signup":false}"""))
        assertEquals(true, AuthRest.googleEnabled("""{"external":{"google":true}}"""))
        assertNull(AuthRest.googleEnabled("oops"))
    }

    @Test fun errorsAreShortHinglish() {
        assertEquals("Email ya password galat hai.", AuthRest.errorText(400, """{"error":"invalid_grant","error_description":"Invalid login credentials"}"""))
        assertEquals("Is email se account pehle se hai — Login dabaiye.", AuthRest.errorText(422, """{"msg":"User already registered"}"""))
        assertTrue(AuthRest.validEmail("dev@gmail.com")); assertFalse(AuthRest.validEmail("dev@"))
    }
}
