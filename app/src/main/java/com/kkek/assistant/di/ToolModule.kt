package com.kkek.assistant.di

import com.kkek.assistant.System.TTSHelper
import com.kkek.assistant.System.touch.AccessibilityHelper
import com.kkek.assistant.data.AssistantRepository
import com.kkek.assistant.domain.model.AiTool
import com.kkek.assistant.music.SpotifyHelper
import com.kkek.assistant.tools.CallTool
import com.kkek.assistant.tools.LaunchAppTool
import com.kkek.assistant.tools.NotificationTool
import com.kkek.assistant.tools.SpotifyTool
import com.kkek.assistant.tools.StatusTool
import com.kkek.assistant.tools.TouchTool
import com.kkek.assistant.tools.TtsTool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object ToolModule {

    @Provides
    @IntoSet
    fun provideLaunchAppTool(): AiTool = LaunchAppTool()

    @Provides
    @IntoSet
    fun provideTtsTool(ttsHelper: TTSHelper): AiTool = TtsTool(ttsHelper)

    @Provides
    @IntoSet
    fun provideTouchTool(accessibilityHelper: AccessibilityHelper): AiTool = TouchTool(accessibilityHelper)

    @Provides
    @IntoSet
    fun provideSpotifyTool(spotifyHelper: SpotifyHelper): AiTool = SpotifyTool(spotifyHelper)

    @Provides
    @IntoSet
    fun provideNotificationTool(repository: AssistantRepository): AiTool = NotificationTool(repository)

    @Provides
    @IntoSet
    fun provideStatusTool(repository: AssistantRepository): AiTool = StatusTool(repository)

    @Provides
    @IntoSet
    fun provideCallTool(repository: AssistantRepository): AiTool = CallTool(repository)
}
