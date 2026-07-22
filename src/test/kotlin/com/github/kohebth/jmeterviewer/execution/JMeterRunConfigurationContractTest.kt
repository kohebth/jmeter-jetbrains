package com.github.kohebth.jmeterviewer.execution

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class JMeterRunConfigurationContractTest {
    @Test
    fun temporaryRunsArePinnedToAFileParallelAndDoNotActivateTheRunToolWindow() {
        val workspaceSource = source("editor/JMeterWorkspaceService.kt")
        val configurationSource = source("execution/JMeterRunConfiguration.kt")

        assertTrue(workspaceSource.contains("setActivateToolWindowBeforeRun(false)"))
        assertTrue(workspaceSource.contains("setAllowRunningInParallel(true)"))
        assertTrue(workspaceSource.contains("targetFileUrl = editor.virtualFile.url"))
        assertTrue(configurationSource.contains("targetFileUrl"))
        assertTrue(configurationSource.contains("prepareExternalRun(targetFileUrl, mode)"))
    }

    private fun source(relative: String): String = Files.readString(
        Path.of("src/main/kotlin/com/github/kohebth/jmeterviewer").resolve(relative),
    )
}
