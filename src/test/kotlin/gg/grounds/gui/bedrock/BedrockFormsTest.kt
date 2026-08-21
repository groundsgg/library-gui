package gg.grounds.gui.bedrock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * These pin the wire format against Cumulus's own codecs. Every key and every `type` value below
 * was read out of `ModalFormCodec`, `CustomFormCodec`, `FormType` and `ComponentType` in
 * GeyserMC/Cumulus — a rename there is a form that silently never appears, which is exactly the
 * failure a test should catch rather than a player.
 */
class BedrockFormsTest {

    @Test
    fun `modal json matches cumulus field names`() {
        assertEquals(
            """{"type":"modal","title":"Delete map?","content":"This cannot be undone.",""" +
                """"button1":"Delete","button2":"Keep"}""",
            BedrockForms.modalJson("Delete map?", "This cannot be undone.", "Delete", "Keep"),
        )
    }

    @Test
    fun `custom form json carries a single input component`() {
        assertEquals(
            """{"type":"custom_form","title":"Rename","content":[{"type":"input",""" +
                """"text":"New name","placeholder":"e.g. crater","default":"crater"}]}""",
            BedrockForms.customInputJson("Rename", "New name", "e.g. crater", "crater"),
        )
    }

    @Test
    fun `player text is escaped rather than breaking the payload`() {
        // A player types the quote; without escaping this truncates the JSON and the form is
        // dropped by the client with nothing in any log.
        val json = BedrockForms.customInputJson("t", "l", "p", """say "hi"\ok""")
        assertEquals(
            """{"type":"custom_form","title":"t","content":[{"type":"input",""" +
                """"text":"l","placeholder":"p","default":"say \"hi\"\\ok"}]}""",
            json,
        )
    }

    @Test
    fun `newlines and control characters are escaped`() {
        assertEquals(
            """{"type":"modal","title":"a\nb","content":"c\td","button1":"e","button2":"f"}""",
            BedrockForms.modalJson("a\nb", "c\td", "e", "f"),
        )
    }

    @Test
    fun `custom form response yields the typed text`() {
        assertEquals("crater", BedrockForms.firstStringOfJsonArray("""["crater"]"""))
    }

    @Test
    fun `custom form response unescapes what the player typed`() {
        assertEquals("""say "hi"""", BedrockForms.firstStringOfJsonArray("""["say \"hi\""]"""))
        assertEquals("a\nb", BedrockForms.firstStringOfJsonArray("""["a\nb"]"""))
        assertEquals("é", BedrockForms.firstStringOfJsonArray("""["é"]"""))
    }

    @Test
    fun `a dismissed form reads as no answer`() {
        // Geyser answers a dismissed form with an empty or null body, and every caller here treats
        // that as "the player said nothing" rather than as the empty string.
        assertNull(BedrockForms.firstStringOfJsonArray(null))
        assertNull(BedrockForms.firstStringOfJsonArray(""))
        assertNull(BedrockForms.firstStringOfJsonArray("null"))
    }

    @Test
    fun `an empty text answer is still an answer`() {
        assertEquals("", BedrockForms.firstStringOfJsonArray("""[""]"""))
    }
}
