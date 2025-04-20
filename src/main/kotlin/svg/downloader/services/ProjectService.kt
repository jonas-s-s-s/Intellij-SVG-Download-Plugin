package svg.downloader.services

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.io.IOException

@Service(Service.Level.PROJECT)
class ProjectService(private val project: Project) {

    /**
     * Returns the project root path as a string
     */
    fun getProjectRootPath(): String? {
        return project.basePath
    }

    /**
     * Checks if the given path is valid and exists
     */
    fun isPathValid(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.isDirectory
    }

    /**
     * Writes a file with the given name and content into the specified path within the project
     */
    fun writeFileInProject(path: String, fileName: String, content: String) {
        val targetPath = File(path)
        val targetDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetPath) ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val file: VirtualFile = targetDir.findOrCreateChildData(this, fileName)
                VfsUtil.saveText(file, content)
            } catch (e: IOException) {
                thisLogger().warn("Failed to write file: $fileName at $path", e)
            }
        }
    }

    /**
     * Writes an SVG file, checks for duplicate files and avoids rewriting them
     */
    fun writeSvg(path: String, fileName: String, content: String) {
        val targetDirFile = File(path)
        val targetDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetDirFile) ?: run {
            thisLogger().warn("Target directory not found: $path")
            return
        }

        val dotIndex = fileName.lastIndexOf('.')
        val (base, ext) = if (dotIndex != -1) {
            Pair(fileName.substring(0, dotIndex), fileName.substring(dotIndex + 1))
        } else {
            Pair(fileName, "")
        }

        var candidateName = fileName
        var counter = 1

        while (targetDir.findChild(candidateName) != null) {
            candidateName = if (ext.isNotEmpty()) {
                "${base}_${counter}.$ext"
            } else {
                "${base}_${counter}"
            }
            counter += 1
        }

        writeFileInProject(path, candidateName, content)
    }
}