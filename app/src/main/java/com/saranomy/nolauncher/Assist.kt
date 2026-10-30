package com.saranomy.nolauncher

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Экран списка приложений в режиме помощника: закрывается после запуска приложения. */
class AssistActivity : NoLauncher() {
    override val assistMode: Boolean
        get() = true
}

class AssistService : VoiceInteractionService()

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return AssistSession(this)
    }
}

class AssistSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, AssistActivity::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startAssistantActivity(intent)
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (ignored: Exception) {
            }
        }
    }
}

/** Заглушка: Android требует распознаватель речи у приложений-помощников. Ничего не распознаёт. */
class StubRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: RecognitionService.Callback?) {
        try {
            listener?.error(SpeechRecognizer.ERROR_CLIENT)
        } catch (ignored: Exception) {
        }
    }

    override fun onCancel(listener: RecognitionService.Callback?) {}

    override fun onStopListening(listener: RecognitionService.Callback?) {}
}
