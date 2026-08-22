package com.zmastery.english.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zmastery.english.viewmodel.AppViewModel

/**
 * Measures REAL time-on-screen for a learning surface and banks it as study
 * time. Drop this at the top of any learning screen:
 *
 *     TrackStudyTime(vm, "review")
 *
 * Behaviour:
 *  • starts a session when the screen enters composition (and on ON_RESUME)
 *  • banks the elapsed time on ON_PAUSE and on dispose (navigating away)
 *  • the ViewModel clamps any single stretch, so an app left open in the
 *    background can never inflate the statistics
 */
@Composable
fun TrackStudyTime(vm: AppViewModel, label: String) {
    val owner = LocalLifecycleOwner.current

    // Start immediately on first composition.
    LaunchedEffect(label) { vm.beginStudySession(label) }

    DisposableEffect(owner, label) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.beginStudySession(label)
                Lifecycle.Event.ON_PAUSE -> vm.endStudySession()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            // Navigating away — bank whatever was earned.
            vm.endStudySession()
        }
    }
}
