package de.mm20.launcher2.ui.component.preferences

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SliderPreferenceTest {
    @get:Rule val compose = createComposeRule()
    private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun externalPreferenceChangesUpdateTheThumb() {
        val value = mutableIntStateOf(48)
        compose.setContent {
            MaterialTheme { SliderPreference("Icon size", value = value.intValue, min = 24, max = 96, onValueChanged = {}) }
        }
        compose.runOnIdle { value.intValue = 32 }
        val current = compose.onNode(slider).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals(32f, current, 0f)
    }

    @Test
    fun disabledPreferencesDisableTheActualSlider() {
        compose.setContent {
            MaterialTheme { SliderPreference("Count", value = 0, min = 0, max = 60, enabled = false, onValueChanged = {}) }
        }
        compose.onNode(slider).assertIsNotEnabled()
    }
}
