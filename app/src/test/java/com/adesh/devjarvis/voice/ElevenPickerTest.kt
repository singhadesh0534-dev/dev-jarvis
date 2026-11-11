package com.adesh.devjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElevenPickerTest {
    private val voices = """{"voices":[
        {"voice_id":"f1","name":"Sarah","category":"premade","labels":{"gender":"female","accent":"american"}},
        {"voice_id":"m1","name":"Will","category":"premade","labels":{"gender":"male","accent":"american","age":"young"}},
        {"voice_id":"d1","name":"Daniel - Steady Broadcaster","category":"premade","labels":{"gender":"male","accent":"british","age":"middle_aged","descriptive":"formal"}},
        {"voice_id":"x1","name":"Mystery"}
    ]}"""

    @Test fun picksTheDeepBritishMaleVoice() = assertEquals("d1", ElevenPicker.pickVoice(voices))

    @Test fun neverAFemaleVoiceAndNullWhenNothingFits() {
        assertNull(ElevenPicker.pickVoice("""{"voices":[{"voice_id":"f1","name":"Sarah","labels":{"gender":"female"}}]}"""))
        assertNull(ElevenPicker.pickVoice("not json"))
        assertEquals("m1", ElevenPicker.pickVoice("""{"voices":[{"voice_id":"m1","name":"Will","labels":{"gender":"male"}}]}"""))
    }

    @Test fun picksTheFastestModelThatSpeaksHindi() {
        val models = """[
            {"model_id":"eleven_multilingual_v2","can_do_text_to_speech":true,"languages":[{"language_id":"en"},{"language_id":"hi"}]},
            {"model_id":"eleven_flash_v2_5","can_do_text_to_speech":true,"languages":[{"language_id":"en"},{"language_id":"hi"}]},
            {"model_id":"eleven_english_sts_v2","can_do_text_to_speech":false,"languages":[{"language_id":"en"}]}
        ]"""
        assertEquals("eleven_flash_v2_5", ElevenPicker.pickModel(models))
        assertEquals("eleven_multilingual_v2", ElevenPicker.pickModel("""[{"model_id":"eleven_flash_v2_5","languages":[{"language_id":"en"}]},{"model_id":"eleven_multilingual_v2","languages":[{"language_id":"hi"}]}]"""))
        assertEquals("eleven_turbo_v2_5", ElevenPicker.pickModel("""[{"model_id":"eleven_turbo_v2_5","languages":[]}]"""))
        assertNull(ElevenPicker.pickModel("[]"))
    }
}
