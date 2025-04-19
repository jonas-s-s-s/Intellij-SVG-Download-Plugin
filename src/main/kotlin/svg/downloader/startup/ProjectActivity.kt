package svg.downloader.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
    }
}