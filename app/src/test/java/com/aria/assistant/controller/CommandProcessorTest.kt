package com.aria.assistant.controller

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommandProcessorTest {

    private val processor = CommandProcessor(ApplicationProvider.getApplicationContext())

    @Test
    fun greetingIsHandled() {
        val result = processor.processCommand("hola")
        assertTrue(result.handled)
        assertTrue(result.response.contains("ARIA"))
    }

    @Test
    fun webSearchProducesSearchAction() {
        val result = processor.processCommand("busca gatos en internet")
        assertTrue(result.handled)
        assertTrue(result.action is SystemAction.WebSearch)
    }

    @Test
    fun alarmCommandParsesHourAndMinute() {
        val result = processor.processCommand("pon una alarma a las 7:30")
        assertTrue(result.handled)
        val action = result.action
        assertTrue(action is SystemAction.SetAlarm)
        action as SystemAction.SetAlarm
        assertEquals(7, action.hour)
        assertEquals(30, action.minute)
    }

    @Test
    fun timeQueryIsHandled() {
        val result = processor.processCommand("qué hora es")
        assertTrue(result.handled)
        assertTrue(result.response.startsWith("Son las"))
    }

    @Test
    fun unknownCommandIsNotHandled() {
        val result = processor.processCommand("zxcvbnm asdf")
        assertFalse(result.handled)
    }
}
