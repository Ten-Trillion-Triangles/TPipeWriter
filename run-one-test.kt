@file:JvmName("RunOneTest")

import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.listeners.TestExecutionSummary
import java.io.PrintWriter

fun main() {
    val testClass = System.getenv("TEST_CLASS")
        ?: error("Set TEST_CLASS env var to fully qualified test class name")

    val request = LauncherDiscoveryRequestBuilder.request()
        .selectors(selectClass(testClass))
        .build()

    val launcher = LauncherFactory.create()
    val listener = SummaryGeneratingListener()
    launcher.registerTestExecutionListeners(listener)
    launcher.execute(request)

    val summary: TestExecutionSummary = listener.summary
    val pw = PrintWriter(System.out, true)
    summary.printTo(pw)
    summary.printFailuresTo(pw)

    if (summary.totalFailureCount > 0) {
        System.exit(1)
    }
}
