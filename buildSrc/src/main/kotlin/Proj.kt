import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.project

object Proj {
  const val annotation = ":annotations"
  const val compiler = ":compiler"
  const val deeplink = ":deeplink"
  const val defaultRes = ":defaultRes"
}
