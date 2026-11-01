package com.saranomy.nolauncher

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
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
    override fun onCreate() {
        super.onCreate()
        // Своего окна у сессии нет: интерфейс показывает AssistActivity.
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        NoLauncher.lastActivity = null
        NoLauncher.assistState =
            if ((showFlags and VoiceInteractionSession.SHOW_WITH_ASSIST) != 0) 1 else 0
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

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        super.onHandleAssist(data, structure, content)
        val component = try {
            structure?.activityComponent
        } catch (e: Exception) {
            null
        }
        val pkg = data?.getString(Intent.EXTRA_ASSIST_PACKAGE)
        NoLauncher.lastActivity = when {
            component != null -> component.flattenToString()
            pkg != null -> pkg + " (activity unknown)"
            else -> null
        }
        NoLauncher.assistState = 2
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
